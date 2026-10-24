(() => {
  const cfg = window.CODEDROP_CONFIG || {};
  const configured = !!(window.supabase && cfg.supabaseUrl && cfg.supabaseAnonKey && !cfg.supabaseUrl.includes("YOUR-") && !cfg.supabaseAnonKey.includes("YOUR_"));
  const $ = (id) => document.getElementById(id);
  const ui = {
    fileInput: $("fileInput"), fileList: $("fileList"), empty: $("emptyState"), auth: $("authDialog"), authForm: $("authForm"), deleteDialog: $("deleteDialog"), toastRegion: $("toastRegion"),
    filesPage: $("filesPage"), downloadPage: $("downloadPage"), search: $("searchInput"), sort: $("sortSelect")
  };
  const state = { client: null, user: null, files: [], signup: false, pendingDelete: null, busy: false };
  const MAX_BYTES = 10 * 1024 * 1024;

  function toast(message, type = "") {
    const node = document.createElement("div"); node.className = `toast ${type}`; node.textContent = message; ui.toastRegion.append(node);
    window.setTimeout(() => node.remove(), 4200);
  }
  function fmtSize(bytes) {
    if (!bytes) return "0 B";
    const units = ["B", "KB", "MB", "GB"]; const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / (1024 ** index)).toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
  }
  function fmtDate(date) {
    return new Intl.DateTimeFormat("he-IL", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(date));
  }
  function isJava(file) { return file && file.name.toLowerCase().endsWith(".java") && (!file.type || file.type === "text/plain" || file.type === "text/x-java-source" || file.type === "application/octet-stream"); }
  function safeName(name) { return name.normalize("NFKC").replace(/[\\/\u0000-\u001f]/g, "_").replace(/\.\./g, "_").slice(0, 180); }
  function openAuth() { if (!configured) { toast("צריך לחבר פרויקט Supabase. פתח/י את README-he.md להוראות.", "error"); return; } ui.auth.showModal(); }
  function setAuthMode(signup) {
    state.signup = signup;
    $("authTitle").textContent = signup ? "יוצרים מרחב משלך." : "נכנסים לעניינים.";
    $("authSubtitle").textContent = signup ? "חשבון חדש, מרחב קבצים פרטי." : "התחבר/י כדי לגשת לקבצים שלך.";
    $("authSubmit").innerHTML = signup ? 'יצירת חשבון <span>←</span>' : 'התחברות <span>←</span>';
    $("authSwitchText").textContent = signup ? "כבר יש לך חשבון?" : "אין לך חשבון?";
    $("authSwitch").textContent = signup ? "התחברות" : "יצירת חשבון";
    $("confirmField").hidden = !signup;
    $("authPassword").autocomplete = signup ? "new-password" : "current-password";
    $("authConfirm").required = signup;
  }
  function setUser(user) {
    state.user = user;
    $("userName").textContent = user ? (user.user_metadata?.name || user.email?.split("@")[0] || "החשבון שלך") : "לא מחובר";
    $("userEmail").textContent = user ? user.email : "התחבר כדי להתחיל";
    $("avatar").textContent = user ? (user.email?.[0] || "U").toUpperCase() : "?";
    $("logoutButton").hidden = !user;
    // The top upload action should open sign-in while signed out.
    $("uploadTopButton").disabled = false;
    // Keep the empty-state button enabled while signed out: it opens auth.
    $("emptyUploadButton").disabled = false;
    if (!user) { state.files = []; renderFiles(); }
  }
  function updateStats() {
    const bytes = state.files.reduce((n, file) => n + (Number(file.size_bytes) || 0), 0);
    $("fileCount").textContent = state.files.length;
    $("filesCountChip").textContent = state.files.length;
    $("navCount").textContent = state.files.length;
    $("statStorage").textContent = fmtSize(bytes);
    $("storageValue").textContent = fmtSize(bytes);
    $("storageBar").style.width = `${Math.min(bytes / (100 * 1024 * 1024) * 100, 100)}%`;
    const newest = [...state.files].sort((a, b) => new Date(b.created_at) - new Date(a.created_at))[0];
    $("lastFile").textContent = newest ? newest.file_name : "—";
  }
  async function loadFiles() {
    if (!state.client || !state.user) return;
    const { data, error } = await state.client.from("java_files").select("id,file_name,storage_path,size_bytes,created_at").order("created_at", { ascending: false });
    if (error) { toast("לא הצלחנו לטעון את הקבצים. בדוק/י את הגדרות Supabase.", "error"); console.error(error); return; }
    state.files = data || []; renderFiles();
  }
  function actionButton(label, glyph, className, callback) {
    const button = document.createElement("button"); button.className = `row-action ${className || ""}`; button.type = "button"; button.title = label; button.setAttribute("aria-label", label); button.textContent = glyph; button.addEventListener("click", callback); return button;
  }
  function renderFiles() {
    updateStats(); ui.fileList.replaceChildren();
    const query = ui.search.value.trim().toLowerCase(); const sort = ui.sort.value;
    const files = state.files.filter((file) => file.file_name.toLowerCase().includes(query));
    if (sort === "oldest") files.sort((a, b) => new Date(a.created_at) - new Date(b.created_at));
    if (sort === "name") files.sort((a, b) => a.file_name.localeCompare(b.file_name, "he"));
    if (sort === "size") files.sort((a, b) => b.size_bytes - a.size_bytes);
    const noFiles = state.files.length === 0;
    ui.empty.hidden = !noFiles || !state.user;
    ui.fileList.hidden = noFiles || !state.user;
    if (!state.user) { ui.empty.hidden = false; ui.fileList.hidden = true; $("emptyState").querySelector("h3").textContent = "הקבצים שלך מחכים לך"; $("emptyState").querySelector("p").textContent = "התחבר/י או צרו חשבון כדי להעלות ולנהל קובצי Java."; $("emptyUploadButton").textContent = "התחברות / יצירת חשבון"; $("emptyUploadButton").onclick = openAuth; return; }
    $("emptyState").querySelector("h3").textContent = "מתחילים עם קובץ ראשון";
    $("emptyState").querySelector("p").textContent = "העלה/י קובץ ‎.java והוא יופיע כאן, במרחב הפרטי שלך.";
    $("emptyUploadButton").innerHTML = "<span>＋</span> בחירת קובץ"; $("emptyUploadButton").onclick = () => ui.fileInput.click();
    if (!files.length && !noFiles) { const empty = document.createElement("div"); empty.className = "empty-filter"; empty.textContent = "לא נמצאו קבצים לפי החיפוש."; empty.style.cssText = "padding:35px;text-align:center;color:#858c85;font-size:12px"; ui.fileList.append(empty); return; }
    files.forEach((file) => {
      const row = document.createElement("article"); row.className = "file-row";
      const main = document.createElement("div"); main.className = "file-main";
      const icon = document.createElement("div"); icon.className = "java-file-icon"; icon.textContent = "JAVA";
      const info = document.createElement("div"); info.className = "file-info";
      const name = document.createElement("strong"); name.textContent = file.file_name; name.title = file.file_name;
      const path = document.createElement("small"); path.textContent = "JAVA SOURCE FILE";
      info.append(name, path); main.append(icon, info);
      const size = document.createElement("div"); size.className = "file-meta"; size.textContent = fmtSize(file.size_bytes);
      const date = document.createElement("div"); date.className = "file-date"; date.textContent = fmtDate(file.created_at);
      const owner = document.createElement("span"); owner.className = "file-owner"; owner.textContent = "●  שלך בלבד";
      const actions = document.createElement("div"); actions.className = "row-actions";
      actions.append(actionButton("פתיחת הקובץ", "↗", "", () => downloadFile(file, true)), actionButton("הורדה", "↓", "", () => downloadFile(file, false)), actionButton("מחיקה", "⌫", "delete", () => askDelete(file)));
      row.append(main, size, date, owner, actions); ui.fileList.append(row);
    });
  }
  async function downloadFile(file, open) {
    const { data, error } = await state.client.storage.from("java-files").createSignedUrl(file.storage_path, 60);
    if (error) { toast("לא ניתן לפתוח את הקובץ כרגע.", "error"); console.error(error); return; }
    if (open) window.open(data.signedUrl, "_blank", "noopener,noreferrer");
    else { const link = document.createElement("a"); link.href = data.signedUrl; link.download = file.file_name; link.rel = "noopener"; document.body.append(link); link.click(); link.remove(); }
  }
  async function upload(file) {
    if (!state.user) { openAuth(); return; }
    if (!isJava(file)) { toast("אפשר להעלות קובצי ‎.java בלבד.", "error"); return; }
    if (file.size > MAX_BYTES) { toast("הקובץ גדול מדי. המגבלה היא 10 MB.", "error"); return; }
    if (state.files.some((item) => item.file_name === file.name)) { toast("כבר קיים אצלך קובץ בשם הזה. שנה/י את שם הקובץ ונסה/י שוב.", "error"); return; }
    if (state.busy) return;
    state.busy = true; $("uploadTopButton").disabled = true; toast("מעלה את הקובץ…");
    const id = crypto.randomUUID(); const path = `${state.user.id}/${id}-${safeName(file.name)}`;
    const { error: storageError } = await state.client.storage.from("java-files").upload(path, file, { contentType: "text/x-java-source", upsert: false });
    if (storageError) { state.busy = false; $("uploadTopButton").disabled = false; toast("העלאה נכשלה. בדוק/י את חיבור האינטרנט והגדרות האחסון.", "error"); console.error(storageError); return; }
    const { error: rowError } = await state.client.from("java_files").insert({ id, owner_id: state.user.id, file_name: safeName(file.name), storage_path: path, size_bytes: file.size });
    if (rowError) {
      await state.client.storage.from("java-files").remove([path]); state.busy = false; $("uploadTopButton").disabled = false;
      toast("הקובץ הועלה, אבל שמירת הפרטים נכשלה. נסה/י שוב.", "error"); console.error(rowError); return;
    }
    state.busy = false; $("uploadTopButton").disabled = false; await loadFiles(); toast("הקובץ נוסף למרחב הפרטי שלך.", "success");
  }
  function askDelete(file) { state.pendingDelete = file; $("deleteFileName").textContent = file.file_name; ui.deleteDialog.showModal(); }
  async function deleteFile() {
    const file = state.pendingDelete; if (!file || !state.client) return;
    $("confirmDelete").disabled = true;
    const { error: storageError } = await state.client.storage.from("java-files").remove([file.storage_path]);
    if (storageError) { toast("מחיקת הקובץ נכשלה.", "error"); console.error(storageError); $("confirmDelete").disabled = false; return; }
    const { error } = await state.client.from("java_files").delete().eq("id", file.id);
    if (error) { toast("הקובץ הוסר מהאחסון, אבל עדכון הרשומה נכשל.", "error"); console.error(error); }
    else toast("הקובץ נמחק.", "success");
    $("confirmDelete").disabled = false; ui.deleteDialog.close(); state.pendingDelete = null; await loadFiles();
  }
  function showPage(page) {
    const download = page === "download"; ui.filesPage.classList.toggle("active", !download); ui.downloadPage.classList.toggle("active", download);
    document.querySelectorAll(".nav-item").forEach((item) => item.classList.toggle("active", item.dataset.page === page));
    $("crumb").textContent = download ? "הורדות" : "הקבצים שלי"; document.title = download ? "הורדות — CodeDrop" : "הקבצים שלי — CodeDrop";
    document.querySelector(".sidebar").classList.remove("open");
  }
  function setupDownload() {
    const button = $("downloadButton");
    if (cfg.downloadUrl) {
      // Let the browser handle the same-origin static file directly. Fetching
      // into a Blob can fail on GitHub Pages/CDN responses even when the URL works.
      button.href = new URL(cfg.downloadUrl, window.location.href).href;
      button.classList.remove("disabled"); button.classList.add("ready"); button.removeAttribute("aria-disabled");
      button.removeAttribute("target"); button.removeAttribute("rel");
      let fileName = cfg.downloadFileName || "";
      if (!fileName) { try { fileName = decodeURIComponent(new URL(cfg.downloadUrl, window.location.href).pathname.split("/").pop()); } catch { fileName = "download.java"; } }
      if (fileName) button.download = fileName;
      $("downloadButtonText").textContent = "הורדת קובץ Java";
      $("downloadBadge").textContent = "זמין להורדה"; $("downloadBadge").style.color = "var(--lime)";
      $("downloadDescription").textContent = `הורידו את ${fileName} למחשב שלכם.`;
      $("downloadMeta").textContent = cfg.downloadLabel || "JAVA SOURCE · קובץ Java";
    }
    else button.addEventListener("click", (event) => event.preventDefault());
  }
  document.querySelectorAll(".nav-item").forEach((item) => item.addEventListener("click", (event) => { event.preventDefault(); history.replaceState(null, "", item.getAttribute("href")); showPage(item.dataset.page); }));
  $("uploadTopButton").addEventListener("click", () => state.user ? ui.fileInput.click() : openAuth());
  $("emptyUploadButton").addEventListener("click", () => state.user ? ui.fileInput.click() : openAuth());
  ui.fileInput.addEventListener("change", (event) => { const file = event.target.files?.[0]; if (file) upload(file); event.target.value = ""; });
  ui.search.addEventListener("input", renderFiles); ui.sort.addEventListener("change", renderFiles);
  $("authSwitch").addEventListener("click", () => setAuthMode(!state.signup));
  $("authForm").addEventListener("submit", async (event) => {
    event.preventDefault(); if (!state.client) return;
    const email = $("authEmail").value.trim(); const password = $("authPassword").value;
    if (state.signup && password !== $("authConfirm").value) { toast("הסיסמאות לא תואמות.", "error"); return; }
    const submit = $("authSubmit"); submit.disabled = true; submit.textContent = state.signup ? "יוצרים חשבון…" : "מתחברים…";
    const result = state.signup ? await state.client.auth.signUp({ email, password }) : await state.client.auth.signInWithPassword({ email, password });
    submit.disabled = false; setAuthMode(state.signup);
    if (result.error) { toast(result.error.message.includes("Invalid login") ? "האימייל או הסיסמה אינם נכונים." : result.error.message, "error"); return; }
    if (state.signup && !result.data.session) { ui.auth.close(); toast("שלחנו אליך מייל לאימות החשבון. אחרי האימות אפשר להתחבר.", "success"); return; }
    ui.auth.close(); ui.authForm.reset(); toast(state.signup ? "החשבון נוצר בהצלחה." : "התחברת בהצלחה.", "success");
  });
  $("logoutButton").addEventListener("click", async () => { await state.client.auth.signOut(); toast("התנתקת מהחשבון."); });
  $("cancelDelete").addEventListener("click", () => ui.deleteDialog.close()); $("confirmDelete").addEventListener("click", deleteFile);
  $("mobileMenu").addEventListener("click", () => document.querySelector(".sidebar").classList.toggle("open"));
  window.addEventListener("hashchange", () => showPage(location.hash === "#download" ? "download" : "files"));
  setupDownload(); setAuthMode(false); showPage(location.hash === "#download" ? "download" : "files");
  if (configured) {
    state.client = window.supabase.createClient(cfg.supabaseUrl, cfg.supabaseAnonKey);
    state.client.auth.getSession().then(({ data }) => setUser(data.session?.user || null));
    state.client.auth.onAuthStateChange((_event, session) => { setUser(session?.user || null); if (session?.user) setTimeout(loadFiles, 0); });
  } else {
    setUser(null);
    window.addEventListener("DOMContentLoaded", () => { const p = $("emptyState").querySelector("p"); p.textContent = "חברו את האתר ל־Supabase כדי ליצור חשבון ולהתחיל להעלות קובצי Java. ההוראות בקובץ README-he.md."; });
  }
})();
