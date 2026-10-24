package com.extreme.audioamplifier

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashText = intent.getStringExtra("crash_text")
            ?: CrashHandler.getLastCrash(this)
            ?: "No crash log found"

        val logView = TextView(this).apply {
            text = crashText
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
            textSize = 12f
            setTextColor(0xFFEEEEEE.toInt())
        }

        val scroll = ScrollView(this).apply {
            addView(logView)
            setBackgroundColor(0xFF1A1A1A.toInt())
        }

        val copyBtn = Button(this).apply {
            text = "COPY LOG"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", crashText))
                Toast.makeText(this@ErrorActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            addView(copyBtn)
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(layout)
    }
}
