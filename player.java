/*

         * מדריך מהיר לכתיבת האלגוריתם שלך:
         מה שרשום בקובץ לא לשנות אלא אם כן רשום לשנות!
          אזהרה! אם תשנו משהו בטעות זה יכול להיות שגיאה ולאחר מכן במשחק תקבלו קיק אוטומטי בגלל שגיאה באלגוריתם!

         שנו את שם הקובץ לשם שתרצו.
         * משתנים שעומדים לרשותך:
         * - myX, myY: המיקום הנוכחי שלך על הלוח (X ו-Y).
         * - state.getTargets(): רשימת כל המטרות על הלוח (לכל מטרה יש t.x ו-t.y).
         * - state.getWidth(), state.getHeight(): מימדי הלוח.

         * ערכים שחובה להחזיר (return) - לאן לזוז:
         * 0 = למעלה (UP)
         * 1 = ימינה (RIGHT)
         * 2 = למטה (DOWN)
         * 3 = שמאלה (LEFT)


         אפשרות 1: תנועה אקראית רגילה

         return rand.nextInt(4); <-- יש כאן ראנדום אז חשוב לזכור לייבא את הספרייה


         אפשרות 2: הליכה אל המטרה הכי קרובה אליך

        Target closestTarget = null;
        double minDistance = Double.MAX_VALUE;

        for (Target t : state.getTargets()) {
            double dist = Math.abs(t.x - myX) + Math.abs(t.y - myY);  מרחק מנהטן
            if (dist < minDistance) {
                minDistance = dist;
                closestTarget = t;
            }
        }

        if (closestTarget != null) {
            if (closestTarget.x > myX) return 1;  ימינה
            if (closestTarget.x < myX) return 3;  שמאלה
            if (closestTarget.y > myY) return 2;  למטה
            if (closestTarget.y < myY) return 0;  למעלה
        }

        */




import GameState;
import PlayerAI;
import Target;


// שנו את שם המחלקה (כרגע זה player) לשם הקובץ
// שימו לב! אם לא תשנו לשם הקובץ זה יראה שגיאה תקבלו קיק אוטומטי!
public class player implements PlayerAI {
    // שנה את שם השחקן(כרגע הוא MyPlayerName) לשמך האמיתי
    public static final char[] PLAYER_NAME_CHARS = "MyPlayerName".toCharArray();

    // התחל לכתוב את הקוד כאן

    }
}