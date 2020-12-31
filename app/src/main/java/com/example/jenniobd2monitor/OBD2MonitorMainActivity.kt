package com.nextgenxr.jenniobd2monitor

//import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.nextgenxr.jenniobd2monitor.R.id.spinner

class OBD2MonitorMainActivity : AppCompatActivity() {

    enum class AUTO_RES {
        AUTO_RES_NONE, AUTO_RES_OK, AUTO_RES_NORMAL, AUTO_RES_ERROR
    }

    private var autoRes: AUTO_RES? = null
    private var mConnectedDeviceName: String? = null

    // Bluetooth
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mOBD2MonitorService: OBD2MonitorService? = null

    // widgets definition
    private var mConnectedStatusTxt: TextView? = null
    private var mResponseMessageTxt: TextView? = null
    private var mSupportedPidsTxt: TextView? = null
    private var mInputOBD2CMDEditTxt: EditText? = null
    private var mSendOBD2CMDBtn: Button? = null
    private var mSelectBtDevicesBtn: Button? = null
    private var mDisconnectDeviceBtn: Button? = null
    private var mCommandsSpinner: Spinner? = null

    // menu items
    private var mItemSetting: MenuItem? = null
    private var mItemQuit: MenuItem? = null
    private var mItemHelp: MenuItem? = null
    private var mItemAutoResOK: MenuItem? = null
    private var mItemAutoResNormal: MenuItem? = null
    private var mItemAutoResError: MenuItem? = null

    private val mMsgHandler: Handler = object : Handler() {
        @Override
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_STATE_CHANGE -> when (msg.arg1) {
                    OBD2MonitorService.STATE_CONNECTING -> setConnectedStatusTitle(R.string.device_connecting)
                    OBD2MonitorService.STATE_CONNECTED -> {
                        mSendOBD2CMDBtn.setEnabled(true)
                        mDisconnectDeviceBtn.setEnabled(true)
                        setConnectedStatusTitle(mConnectedDeviceName)
                    }
                    OBD2MonitorService.STATE_LISTEN, OBD2MonitorService.STATE_NONE -> {
                        mSendOBD2CMDBtn.setEnabled(false)
                        mDisconnectDeviceBtn.setEnabled(false)
                        mConnectedStatusTxt.setText("")
                    }
                    else -> {
                    }
                }
                MESSAGE_READ -> {
                    val readBuf = msg.obj as ByteArray
                    // construct a string from the valid bytes in the buffer
                    val readMessage = String(readBuf, 0, msg.arg1)
                    if (autoRes == AUTO_RES.AUTO_RES_NONE) {
                        setPidsSupported(readMessage)
                        mCmdAndRes!!.append(" > Receive: $readMessage")
                        mCmdAndRes!!.append('\n')
                        mResponseMessageTxt.setText(mCmdAndRes.toString())
                        //                    writeOBD2MonitorLog("Receive: "+ readMessage);
                    } else {
                        autoResponse(readMessage)
                    }
                }
                MESSAGE_WRITE -> {
                }
                MESSAGE_TOAST -> Toast.makeText(applicationContext, msg.data.getString(TOAST),
                        Toast.LENGTH_SHORT).show()
                MESSAGE_DEVICE_NAME ->                     // save the connected device's name
                    mConnectedDeviceName = msg.data.getString(DEVICE_NAME)
                else -> {
                }
            }
        }
    }

    protected override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obd2_monitor_main)
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (mBluetoothAdapter == null)
        {
            Toast.makeText(this, R.string.bt_not_available,
                    Toast.LENGTH_LONG).show()
            finish()
        }

        // Set up local vars to point to UI elements and set some options
        mConnectedStatusTxt = findViewById<TextView>(R.id.connected_status_text)

        mResponseMessageTxt = findViewById<TextView>(R.id.response_msg_text)
        mResponseMessageTxt.setMovementMethod(ScrollingMovementMethod.getInstance())

        mSupportedPidsTxt = findViewById<TextView>(R.id.supported_pids_text)
        mSupportedPidsTxt.setMovementMethod(ScrollingMovementMethod.getInstance())

        mInputOBD2CMDEditTxt = findViewById<EditText>(R.id.input_cmd_edit)
        mSelectBtDevicesBtn = findViewById<Button>(R.id.select_device_btn)

        mSelectBtDevicesBtn.setOnClickListener(
                View.OnClickListener { view -> buttonOnClick(view) })

        mCommandsSpinner = findViewById<Spinner>(spinner)
        val mItems: Array<String> = resources.getStringArray(R.array.ATandOBD2Commands)

        // Set up the adapter and bind the data source
        val mAdapter: ArrayAdapter<String> = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mItems)

        // Bind Adapter to Control
        this.mCommandsSpinner.setAdapter(mAdapter)
        this.mCommandsSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener()
        {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View?, i: Int, l: Long)
                {
                    if (i > 0)
                    {
                        val itemStr: String = adapterView.getItemAtPosition(i).toString()
                        val tmpStr: List<String> = itemStr.split("->")
                        val cmd = tmpStr[0]
                        mInputOBD2CMDEditTxt.setText(cmd)
                    } else {
                        mInputOBD2CMDEditTxt.setText("")
                    }
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?)
                {
                    Toast.makeText(this@OBD2MonitorMainActivity, "Nothing to selected", Toast.LENGTH_LONG).show()
                }
        })
        mCmdAndRes = StringBuilder()
        autoRes = AUTO_RES.AUTO_RES_NONE
    }

    private fun buttonOnClick(v: View)
    {
        when (v.id)
        {
            R.id.send_cmd_btn -> sendOBD2CMD()
            R.id.select_device_btn -> selectDevice()
            R.id.disconnect_device_btn -> disconncetDevice()
            else -> {}
        }
    }

    private fun setupOBDMonitor()
    {
        mSendOBD2CMDBtn = findViewById(R.id.send_cmd_btn) as Button?
        mSendOBD2CMDBtn.setOnClickListener(object : View.OnClickListener()
        {
            override fun onClick(view: View)
            {
                buttonOnClick(view)
            }
        })
        mDisconnectDeviceBtn = findViewById(R.id.disconnect_device_btn) as Button?
        mDisconnectDeviceBtn.setOnClickListener(
                object : View.OnClickListener() {
                    @Override
                    override fun onClick(view: View) {
                        buttonOnClick(view)
                    }
                },
        )
        mOBD2MonitorService = OBD2MonitorService(this, mMsgHandler)
    }

    ////////////////////////////////////////////////////////////
    private fun setConnectedStatusTitle(title: CharSequence?)
    {
        mConnectedStatusTxt?.setText(title)
    }

    ////////////////////////////////////////////////////////////
    private fun setConnectedStatusTitle(resID: Int)
    {
        mConnectedStatusTxt?.setText(resID)
    }

    ////////////////////////////////////////////////////////////
    private fun autoResponse(resMsg: String)
    {
        try
        {
            Thread.sleep(1000)
        } catch (e: InterruptedException)
        {
            e.printStackTrace()
        }

        var responseMessage = resMsg.replace("\r", "")
        responseMessage = responseMessage.trim()
        if (responseMessage == "atz") sendOBD2CMD("ELM327 1.5 >")
        if (responseMessage == "atws") sendOBD2CMD("ELM327 1.5 warm start >")
        if (responseMessage == "ate0") sendOBD2CMD("ok >")
        if (responseMessage == "atl0") sendOBD2CMD("ok >")
        if (responseMessage == "atsp0") sendOBD2CMD("ok >")
        if (responseMessage == "0100") {
            sendOBD2CMD("SEARCHING..." + "41 00 BE 1F A8 13 >")
        }
        if (responseMessage == "0105") {
            if (autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL) sendOBD2CMD("41 05 7B  >")
            if (autoRes == AUTO_RES.AUTO_RES_ERROR) sendOBD2CMD("CAN ERROR >")
        }
        if (responseMessage == "010B") {
            if (autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL) sendOBD2CMD("41 0B 1A >")
            if (autoRes == AUTO_RES.AUTO_RES_ERROR) sendOBD2CMD("CAN ERROR >")
        }
        if (responseMessage == "010C") {
            if (autoRes == AUTO_RES.AUTO_RES_OK) sendOBD2CMD("41 0C 1A F8 >")
            if (autoRes == AUTO_RES.AUTO_RES_NORMAL) sendOBD2CMD("NO DATA >")
            if (autoRes == AUTO_RES.AUTO_RES_ERROR) sendOBD2CMD("CAN ERROR >")
        }
        if (responseMessage == "0101") {
            if (autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL) sendOBD2CMD("41 01 82 07 65 04 >")
            if (autoRes == AUTO_RES.AUTO_RES_ERROR) sendOBD2CMD("CAN ERROR >")
        }
        if (responseMessage == "03") {
            if (autoRes == AUTO_RES.AUTO_RES_OK || autoRes == AUTO_RES.AUTO_RES_NORMAL) sendOBD2CMD("43 00 43 01 33 00 00 >")
            if (autoRes == AUTO_RES.AUTO_RES_ERROR) sendOBD2CMD("CAN ERROR >")
        }
    }

    ////////////////////////////////////////////////////////////
    private fun setPidsSupported(buffer: String)
    {
        var pidSupported: ByteArray? = null
        val flags: StringBuilder = StringBuilder()
        var charBuffer = buffer
        charBuffer = charBuffer.trim()
        charBuffer = charBuffer.replace("\t", "")
        charBuffer = charBuffer.replace(" ", "")
        charBuffer = charBuffer.replace(">", "")
        pidSupported = charBuffer.getBytes()

        if (charBuffer.indexOf("4100") === 0)
        {
            for (i in 0..7)
            {
                val tmp = charBuffer.substring(i + 4, i + 5)
                val data: Int = Integer.valueOf(tmp, 16).intValue()
                //                String retStr = Integer.toBinaryString(data);
                if (data and 0x08 == 0x08) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }

                if (data and 0x04 == 0x04) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }

                if (data and 0x02 == 0x02) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }

                if (data and 0x01 == 0x01) {
                    flags.append("1")
                } else {
                    flags.append("0")
                }
            }

            val supportedPID: StringBuilder = StringBuilder()
            supportedPID.append("Supported PID: ")
            val unSupportedPID: StringBuilder = StringBuilder()
            unSupportedPID.append("Unsupported PID: ")

            ///
            for (j in 0 until flags.length()) {
                if (flags.charAt(j) === '1') {
                    supportedPID.append(" " + PIDS[j] + " ")
                } else {
                    unSupportedPID.append(" " + PIDS[j] + " ")
                }
            }

            supportedPID.append("\n")
            mSupportedPidsTxt?.text = supportedPID.toString() + unSupportedPID.toString()
        } else {
            return
        }
    }

    ////////////////////////////////////////////////////////////
    private fun sendOBD2CMD()
    {
        if (mOBD2MonitorService!!.state != OBD2MonitorService.STATE_CONNECTED)
        {
            Toast.makeText(this, R.string.bt_not_available,
                    Toast.LENGTH_LONG).show()
        }

        var strCMD: String = mInputOBD2CMDEditTxt?.text.toString()
        if (strCMD == "")
        {
            Toast.makeText(this, R.string.please_input_cmd,
                    Toast.LENGTH_LONG).show()
            return
        }

        strCMD += '\r'
        mCmdAndRes!!.append(" > Send: $strCMD")
        mCmdAndRes!!.append('\n')
        mResponseMessageTxt?.text = mCmdAndRes.toString()

        //        byte[] byteCMD =new byte[11];
//        for(int i=0;i<11;i++)
//        {
//            byteCMD[i]= (byte)(Integer.parseInt( strCMD.substring(i * 2, i*2 + 1)));
//        }

        val byteCMD: ByteArray = strCMD.getBytes()
        mOBD2MonitorService!!.write(byteCMD)
        //        writeOBD2MonitorLog("Send: "+ strCMD);
    }


    private fun sendOBD2CMD(sendMsg: String) {
        if (mOBD2MonitorService!!.state != OBD2MonitorService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.bt_not_available,
                    Toast.LENGTH_LONG).show()
        }
        var strCMD = sendMsg
        strCMD += '\r'
        mCmdAndRes!!.append(" > Send: $strCMD")
        mCmdAndRes!!.append('\n')
        mResponseMessageTxt?.text = mCmdAndRes.toString()
        val byteCMD: ByteArray = strCMD.getBytes()
        mOBD2MonitorService!!.write(byteCMD)
        //        writeOBD2MonitorLog("Send: "+ strCMD);
    }


    private fun selectDevice() {
        val devicesIntent = Intent(this@OBD2MonitorMainActivity, OBD2MonitorDevicesActivity::class.java)
        startActivityForResult(devicesIntent, REQUEST_CONNECT_DEVICE_SECURE)
    }

    private fun connectDevice(data: Intent, secure: Boolean) {
        // get bluetooth mac address
        val address: String = data.getExtras().getString(OBD2MonitorDevicesActivity.EXTRA_DEVICE_ADDRESS)
        // Get the bluetooth Device object
        val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(address)
        // Attempt to connect to the device
        mOBD2MonitorService!!.connect(device, secure)
    }

    private fun disconncetDevice() {
        if (mOBD2MonitorService != null) {
            mOBD2MonitorService!!.stop()
        }
    }


    @Override
    fun onActivityResult(requstCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requstCode, resultCode, data)

        when (requstCode) {
            REQUEST_CONNECT_DEVICE_SECURE -> if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    connectDevice(data, true)
                }
            }
            REQUEST_CONNECT_DEVICE_INSECURE -> if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    connectDevice(data, false)
                }
            }
            REQUEST_ENABLE_BT -> if (resultCode == Activity.RESULT_OK) {
                setupOBDMonitor()
            }
            else -> {
            }
        }
    }


    @Override
    protected override fun onStart() {
        super.onStart()
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter?.isEnabled()!!) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
            // Otherwise, setup the chat session
        } else {
            if (mOBD2MonitorService == null) setupOBDMonitor()
        }
    }

    @Override
    protected override fun onPause() {
        super.onPause()
    }


    @Override
    @Synchronized
    protected override fun onResume() {
        super.onResume()
        if (mOBD2MonitorService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mOBD2MonitorService!!.state == OBD2MonitorService.STATE_NONE) {
                // Start the Bluetooth chat services
                mOBD2MonitorService!!.start()
            }
        }
        if (mCmdAndRes.length() > 0) {
            mCmdAndRes.delete(0, mCmdAndRes.length())
        }
    }

    @Override
    protected override fun onStop() {
        super.onStop()
    }

    @Override
    protected override fun onDestroy() {
        super.onDestroy()
        if (mOBD2MonitorService != null) {
            mOBD2MonitorService!!.stop()
        }
        if (mCmdAndRes.length() > 0) {
            mCmdAndRes.delete(0, mCmdAndRes.length())
        }
    }

    @Override
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_obd2_monitor_main, menu)
        mItemSetting = menu.findItem(R.id.menu_settings)
        mItemQuit = menu.findItem(R.id.menu_quit)
        mItemHelp = menu.findItem(R.id.menu_help)
        mItemAutoResOK = menu.findItem(R.id.menu_res_ok)
        mItemAutoResNormal = menu.findItem(R.id.menu_res_normal)
        mItemAutoResError = menu.findItem(R.id.menu_res_error)
        return true
    }


    @Override
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> Toast.makeText(this, "Menu_Setting_Clicked", Toast.LENGTH_LONG).show()
            R.id.menu_quit -> finish()
            R.id.menu_help -> {
                val helpIntent = Intent()
                helpIntent.setClass(this, OBD2MonitorHelpActivity::class.java)
                startActivity(helpIntent)
            }
            R.id.menu_res_ok -> autoRes = AUTO_RES.AUTO_RES_OK
            R.id.menu_res_normal -> autoRes = AUTO_RES.AUTO_RES_NORMAL
            R.id.menu_res_error -> autoRes = AUTO_RES.AUTO_RES_ERROR
        }
        //        return super.onOptionsItemSelected(item);
        return true
    }

    companion object {
        // Message types sent from the OBD2MonitorService Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5

        // LogFile
        const val DIR_NAME_OBD2_MONITOR = "OBDIIMonitorLog"
        const val FILE_NAME_OBD2_MONITOR_LOG = "obd2_monitor_log.txt"

        // Key names received from the OBD2MonitorService Handler
        const val DEVICE_NAME = "device_name"
        const val TOAST = "toast"

        // Intent request codes
        private const val REQUEST_CONNECT_DEVICE_SECURE = 1
        private const val REQUEST_CONNECT_DEVICE_INSECURE = 2
        private const val REQUEST_ENABLE_BT = 3
        private var mCmdAndRes: StringBuilder? = null
        private val PIDS = arrayOf(
                "01", "02", "03", "04", "05", "06", "07", "08",
                "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
                "11", "12", "13", "14", "15", "16", "17", "18",
                "19", "1A", "1B", "1C", "1D", "1E", "1F", "20")

        // Create LogFile
        fun writeOBD2MonitorLog(content: String) {
            try {
                val rooDir = File(Environment.getExternalStorageDirectory(), DIR_NAME_OBD2_MONITOR)
                if (!rooDir.exists()) {
                    rooDir.mkdirs()
                }
                val logFile = File(rooDir, FILE_NAME_OBD2_MONITOR_LOG)
                if (!logFile.exists()) {
                    logFile.createNewFile()
                }
                if (logFile.canWrite()) {
                    val stime = SimpleDateFormat(
                            "yyyy-MM-dd hh:mm:ss ")
                    val Dfile = RandomAccessFile(logFile, "rw")
                    val contents: String = (stime.format("==" + Date()).toString() + "->" + content
                            + "\r\n")
                    Dfile.seek(Dfile.length())
                    Dfile.write(contents.getBytes("UTF-8"))
                    Dfile.close()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}