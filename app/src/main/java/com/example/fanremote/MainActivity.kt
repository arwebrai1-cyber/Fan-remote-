package com.example.fanremote

import android.app.Activity
import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {

    private val names = listOf(
        "Power", "Speed +", "Speed -", "Timer", "Light", "Breeze / Mode", "Reverse", "Sleep"
    )
    private lateinit var ir: ConsumerIrManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        if (mgr == null || !mgr.hasIrEmitter()) {
            setContentView(TextView(this).apply {
                text = "No IR blaster found on this phone."
                gravity = Gravity.CENTER; textSize = 18f
            })
            return
        }
        ir = mgr

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        root.addView(TextView(this).apply {
            text = "Fan Remote\nTap = send   |   Long-press = set code"
            textSize = 16f
            setPadding(0, 0, 0, 30)
        })
        names.forEach { n -> root.addView(makeButton(n)) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun prefs() = getSharedPreferences("codes", MODE_PRIVATE)

    private fun makeButton(name: String) = Button(this).apply {
        text = name
        textSize = 20f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 190
        ).apply { bottomMargin = 20 }
        setOnClickListener {
            val code = prefs().getString(name, "") ?: ""
            if (code.isBlank()) {
                toast("No code set. Long-press to set one.")
            } else {
                buzz()
                send(code)
            }
        }
        setOnLongClickListener { editCode(name); true }
    }

    private fun editCode(name: String) {
        val input = EditText(this).apply {
            setText(prefs().getString(name, ""))
            hint = "0x00FF807F  or  38000:9000,4500,560,..."
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Code for $name")
            .setMessage("NEC hex (0xAABBCCDD) or raw pattern in microseconds, optionally with frequency prefix like 38000:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs().edit().putString(name, input.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun send(code: String) {
        try {
            if (code.contains(",")) {
                var freq = 38000
                var body = code
                if (code.contains(":")) {
                    freq = code.substringBefore(":").trim().toInt()
                    body = code.substringAfter(":")
                }
                val pattern = body.split(",").map { it.trim().toInt() }.toIntArray()
                ir.transmit(freq, pattern)
            } else {
                ir.transmit(38000, necPattern(code))
            }
        } catch (e: Exception) {
            toast("Bad code: ${e.message}")
        }
    }

    // NEC: 9000/4500 header, 560 mark + (560|1690) space per bit, LSB first per byte
    private fun necPattern(hex: String): IntArray {
        val v = hex.trim().removePrefix("0x").removePrefix("0X").toLong(16)
        val bytes = (3 downTo 0).map { ((v shr (it * 8)) and 0xFF).toInt() }
        val p = ArrayList<Int>()
        p.add(9000); p.add(4500)
        for (b in bytes) for (i in 0..7) {
            p.add(560)
            p.add(if ((b shr i) and 1 == 1) 1690 else 560)
        }
        p.add(560)
        return p.toIntArray()
    }

    private fun buzz() {
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26)
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vib.vibrate(30)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
