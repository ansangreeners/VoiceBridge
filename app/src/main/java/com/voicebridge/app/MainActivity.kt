package com.voicebridge.app

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * VoiceBridge - 갤럭시 음성/타이핑을 PC 커서에 블루투스로 직결 입력하는 앱
 *
 * UI 흐름 (카톡식):
 *  1) 하단 메시지입력박스 탭 → 갤럭시 키보드 팝업 (마이크/타이핑 모두 가능)
 *  2) 입력한 글자는 실시간으로 상단 텍스트박스(displayText)에 미러링
 *  3) 키보드 내리고 상단 빈 영역 아무 곳이나 탭 → 자동 전송
 *     (또는 우측 [전송] 버튼)
 *
 * HID 동작:
 *  - 폰을 PC에 블루투스 키보드로 페어링
 *  - 텍스트를 HID 리포트로 한 자씩 전송 (한글은 두벌식 자모로 분해)
 */
class MainActivity : AppCompatActivity() {

    private var btManager: BluetoothManager? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var registered = false

    private lateinit var editor: EditText
    private lateinit var sendBtn: Button
    private lateinit var statusText: TextView
    private lateinit var statusDot: View
    private lateinit var charCount: TextView
    private lateinit var displayText: TextView
    private lateinit var displayScroll: ScrollView
    private lateinit var topTapZone: View

    @Volatile private var sending = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) initBluetooth()
        else statusText.text = "블루투스 권한이 필요합니다"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editor = findViewById(R.id.editor)
        sendBtn = findViewById(R.id.sendBtn)
        statusText = findViewById(R.id.statusText)
        statusDot = findViewById(R.id.statusDot)
        charCount = findViewById(R.id.charCount)
        displayText = findViewById(R.id.displayText)
        displayScroll = findViewById(R.id.displayScroll)
        topTapZone = findViewById(R.id.topTapZone)

        // ── 입력 → 상단 디스플레이 실시간 미러링 + 글자수 갱신 ──
        editor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                displayText.text = text
                charCount.text = "${text.length}자"
                updateSendButton()
                // 자동 스크롤(맨 아래로)
                displayScroll.post {
                    displayScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // ── 우측 [전송] 버튼 ──
        sendBtn.setOnClickListener { triggerSend() }

        // ── 상단 빈 영역 탭 → 키보드 내리고 전송 트리거 ──
        topTapZone.setOnClickListener {
            // 키보드 내리기
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(editor.windowToken, 0)
            editor.clearFocus()
            // 텍스트 있으면 전송
            if (editor.text.isNotEmpty() && hostDevice != null) {
                triggerSend()
            }
        }

        requestPermissions()
    }

    private fun triggerSend() {
        if (sending) return
        if (editor.text.isEmpty() || hostDevice == null) return
        sendText()
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        val need = perms.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permLauncher.launch(perms) else initBluetooth()
    }

    private fun initBluetooth() {
        btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            statusText.text = "블루투스를 켜주세요"
            return
        }

        statusText.text = "HID 프로필 준비 중…"
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerHid()
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = null
                    registered = false
                    setConnected(false, "연결 끊김")
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    private fun registerHid() {
        val hid = hidDevice ?: return
        if (!hasConnectPerm()) return

        // Force discoverable so HID advertisement takes priority over generic phone BT profile
        startActivity(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
        )

        val sdp = BluetoothHidDeviceAppSdpSettings(
            "VoiceBridge",
            "Bluetooth HID Keyboard Device",
            "VoiceBridge",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidConst.REPORT_DESCRIPTOR
        )

        val inQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )
        val outQos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250, BluetoothHidDeviceAppQosSettings.MAX
        )

        try {
            hid.registerApp(sdp, inQos, outQos, { it.run() },
                object : BluetoothHidDevice.Callback() {
                    override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered2: Boolean) {
                        this@MainActivity.registered = registered2
                        runOnUiThread {
                            if (registered2 && hostDevice == null)
                                statusText.text = "PC에서 'VoiceBridge' 페어링하세요"
                        }
                    }
                    override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                        runOnUiThread {
                            if (state == BluetoothProfile.STATE_CONNECTED) {
                                hostDevice = device
                                setConnected(true, device?.name ?: "PC 연결됨")
                            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                                hostDevice = null
                                setConnected(false, "연결 끊김 (다시 연결하세요)")
                            }
                        }
                    }
                })
        } catch (e: SecurityException) {
            statusText.text = "블루투스 권한 오류"
        }
    }

    // ── 텍스트 전송 (핵심) ──
    private fun sendText() {
        val text = editor.text.toString()
        if (text.isEmpty() || hostDevice == null) return

        // 전송 끝에 항상 Enter 1회 (요구사항: "엔터 한 번으로 메시지 전부 넘어감")
        val payload = text + "\n"
        sending = true
        runOnUiThread { sendBtn.isEnabled = false; sendBtn.text = "전송중" }

        Thread {
            try {
                for (ch in payload) {
                    val hangul = HidConst.decomposeHangul(ch)
                    if (hangul != null) {
                        for (k in hangul) {
                            val (mod, key) = HidConst.charToKey(k)
                            if (key != 0) { sendReport(mod, key); sendReport(0, 0); Thread.sleep(8) }
                        }
                    } else {
                        val (mod, key) = HidConst.charToKey(ch)
                        if (key != 0) { sendReport(mod, key); sendReport(0, 0); Thread.sleep(8) }
                    }
                }
            } finally {
                sending = false
                runOnUiThread {
                    editor.setText("")
                    displayText.text = ""
                    sendBtn.text = "전송"
                    updateSendButton()
                    Toast.makeText(this, "✓ PC로 전송 완료", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sendReport(modifier: Int, keyCode: Int) {
        val hid = hidDevice ?: return
        val dev = hostDevice ?: return
        if (!hasConnectPerm()) return
        try {
            val report = byteArrayOf(
                modifier.toByte(), 0,
                keyCode.toByte(), 0, 0, 0, 0, 0
            )
            hid.sendReport(dev, 0, report)
        } catch (e: SecurityException) { /* ignore */ }
    }

    private fun setConnected(connected: Boolean, label: String) {
        statusText.text = label
        statusDot.setBackgroundResource(
            if (connected) R.drawable.dot_green else R.drawable.dot_red
        )
        updateSendButton()
    }

    private fun updateSendButton() {
        sendBtn.isEnabled = (hostDevice != null) && editor.text.isNotEmpty() && !sending
    }

    private fun hasConnectPerm(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (registered && hasConnectPerm()) hidDevice?.unregisterApp()
        } catch (e: SecurityException) {}
    }
}
