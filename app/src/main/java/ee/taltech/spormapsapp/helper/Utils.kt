package ee.taltech.spormapsapp.helper

import android.text.TextUtils
import android.util.Patterns
import java.util.regex.Pattern


object Utils {

    fun isValidEmail(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    fun isValidPassword(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@\$!%*?&])[A-Za-z\\d@\$!%*?&]{6,}\$").matcher(target).matches()
    }

    fun isValidPasswordBasic(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}\$").matcher(target).matches()
    }

    fun isValidPasswordMedium(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
                && Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[a-zA-Z\\d]{6,}$").matcher(target).matches()
    }

    fun isValidName(target: CharSequence): Boolean {
        return !TextUtils.isEmpty(target)
    }
}