package com.extreme.audioamplifier

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val text = TextView(this).apply {
            setTextIsSelectable(true)
            setPadding(32, 32, 32, 32)
            textSize = 12f
            setTextColor(0xFFEEEEEE.toInt())
        }
        scroll.addView(text)
        scroll.setBackgroundColor(0xFF1A1A1A.toInt())

        val crashText = intent.getStringExtra("crash_text")
            ?: CrashHandler.getLastCrash(this)
            ?: "No crash log found"

        text.text = crashText

        // Simple layout with copy button via title
        setContentView(scroll)

        // Long press or add button
        val copyBtn = Button(this).apply {
            text = "COPY LOG"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", crashText))
                Toast.makeText(this@ErrorActivity, "Copied!", Toast.LENGTH_SHORT).show()
            }
        }

        // Re-set with button on top
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            addView(copyBtn)
            addView(scroll, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(layout)
    }
}
