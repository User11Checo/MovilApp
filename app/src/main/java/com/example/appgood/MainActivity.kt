package com.example.appgood

import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private lateinit var tvStatus: TextView
    private lateinit var tvUid: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvTech: TextView
    private lateinit var btnToggleScan: Button
    private lateinit var btnSave: Button
    private lateinit var btnCopy: Button
    private lateinit var btnEmulate: Button
    private lateinit var rvSavedCards: RecyclerView

    private var isScanning = false
    private var isEmulating = false
    private var lastScannedUid: String? = null
    private val savedCards = mutableListOf<String>()
    private lateinit var adapter: SavedCardsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupNfc()
        loadSavedCards()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvUid = findViewById(R.id.tvUid)
        tvSize = findViewById(R.id.tvSize)
        tvTech = findViewById(R.id.tvTech)
        btnToggleScan = findViewById(R.id.btnToggleScan)
        btnSave = findViewById(R.id.btnSave)
        btnCopy = findViewById(R.id.btnCopy)
        btnEmulate = findViewById(R.id.btnEmulate)
        rvSavedCards = findViewById(R.id.rvSavedCards)

        btnToggleScan.setOnClickListener {
            toggleScanning()
        }

        btnSave.setOnClickListener {
            lastScannedUid?.let { saveCard(it) }
        }

        btnCopy.setOnClickListener {
            lastScannedUid?.let { copyToClipboard(it) }
        }

        btnEmulate.setOnClickListener {
            toggleEmulation()
        }

        adapter = SavedCardsAdapter(savedCards)
        rvSavedCards.layoutManager = LinearLayoutManager(this)
        rvSavedCards.adapter = adapter
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            tvStatus.text = getString(R.string.status_not_available)
            btnToggleScan.isEnabled = false
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        intentFiltersArray = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        techListsArray = arrayOf(arrayOf(NfcA::class.java.name), arrayOf(MifareClassic::class.java.name))
    }

    private fun toggleScanning() {
        if (!isScanning) {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
            btnToggleScan.text = getString(R.string.btn_stop_scan)
            btnToggleScan.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)
            tvStatus.text = getString(R.string.status_scanning)
            isScanning = true
        } else {
            nfcAdapter?.disableForegroundDispatch(this)
            btnToggleScan.text = getString(R.string.btn_start_scan)
            btnToggleScan.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)
            tvStatus.text = getString(R.string.status_waiting)
            isScanning = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isScanning) {
            processIntent(intent)
        }
    }

    private fun processIntent(intent: Intent) {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        tag?.let {
            val uid = bytesToHexString(it.id)
            val techs = it.techList.joinToString(", ") { t -> t.substringAfterLast(".") }
            
            var size = -1
            MifareClassic.get(it)?.let { m -> size = m.size }

            updateUiWithTag(uid, techs, size)
        }
    }

    private fun toggleEmulation() {
        val pm = packageManager
        val componentName = ComponentName(this, CardEmulatorService::class.java)
        
        if (!isEmulating) {
            pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            btnEmulate.text = getString(R.string.btn_stop_emulate)
            btnEmulate.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.holo_orange_dark)
            Toast.makeText(this, getString(R.string.msg_emulation_on), Toast.LENGTH_SHORT).show()
            isEmulating = true
        } else {
            pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            btnEmulate.text = getString(R.string.btn_emulate)
            btnEmulate.backgroundTintList = null
            Toast.makeText(this, getString(R.string.msg_emulation_off), Toast.LENGTH_SHORT).show()
            isEmulating = false
        }
    }

    private fun updateUiWithTag(uid: String, techs: String, size: Int) {
        lastScannedUid = uid
        tvUid.text = getString(R.string.label_uid, uid)
        tvSize.text = getString(R.string.label_size, if (size > 0) getString(R.string.bytes, size.toString()) else getString(R.string.unknown))
        tvTech.text = getString(R.string.label_tech, techs)
        btnSave.isEnabled = true
        btnCopy.isEnabled = true
        btnEmulate.isEnabled = true
        tvStatus.text = getString(R.string.status_detected)
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        return bytes.joinToString(":") { String.format("%02X", it) }.uppercase(Locale.getDefault())
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("NFC UID", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.msg_copied), Toast.LENGTH_SHORT).show()
    }

    private fun saveCard(uid: String) {
        if (!savedCards.contains(uid)) {
            savedCards.add(0, uid)
            getSharedPreferences("NFC_CARDS", Context.MODE_PRIVATE).edit().putStringSet("saved_uids", savedCards.toSet()).apply()
            adapter.notifyItemInserted(0)
            Toast.makeText(this, getString(R.string.msg_saved), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedCards() {
        getSharedPreferences("NFC_CARDS", Context.MODE_PRIVATE).getStringSet("saved_uids", null)?.let {
            savedCards.addAll(it)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isScanning) nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onResume() {
        super.onResume()
        if (isScanning) nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }
}

class SavedCardsAdapter(private val cards: List<String>) : RecyclerView.Adapter<SavedCardsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvText: TextView = view.findViewById(android.R.id.text1)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvText.text = cards[position]
    }
    override fun getItemCount() = cards.size
}
