package com.singhealth.enhance.security

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.os.AsyncTask
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutionException

class LogOutTimerUtil {

    interface LogOutListener {
        fun doLogout()
    }

    companion object {
        var longTimer: Timer? = null
        val LOGOUT_TIME =
            12000000 // 20 min is 1200000 ms, for testing use 5 sec which is 5000 ms

        fun startLogoutTimer(context: Context?, logOutListener: LogOutListener) {
            if (longTimer != null) {
                longTimer!!.cancel()
                longTimer = null
            }
            if (longTimer == null) {
                longTimer = Timer()
                longTimer!!.schedule(object : TimerTask() {
                    override fun run() {
                        println("Timer Started")
                        cancel()
                        longTimer = null
                        try {
                            val foreGround = ForegroundCheckTask().execute(context).get()
                            if (!foreGround) {
                                println("Session Logout")
                                logOutListener.doLogout()
                            } else {
                                startLogoutTimer(context, logOutListener)
                            }
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        } catch (e: ExecutionException) {
                            e.printStackTrace()
                        }
                    }
                }, LOGOUT_TIME.toLong())
            }
        }

        fun stopLogoutTimer() {
            if (longTimer != null) {
                println("Timer Stopped")
                longTimer!!.cancel()
                longTimer = null
            }
        }
    }

    internal class ForegroundCheckTask :
        AsyncTask<Context?, Void?, Boolean>() {
        override fun doInBackground(vararg params: Context?): Boolean? {
            val context: Context = params[0]!!.getApplicationContext()
            return isAppOnForeground(context)
        }

        private fun isAppOnForeground(context: Context): Boolean {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val appProcesses = activityManager.runningAppProcesses ?: return false
            val packageName: String = context.getPackageName()
            for (appProcess in appProcesses) {
                if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                    return true
                }
            }
            return false
        }
    }
}