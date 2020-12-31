package com.nextgenxr.jenniobd2monitor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.widget.*

public class OBD2MonitorDevicesActivity  : AppCompatActivity() {

    // Member fields
    private var mBtAdapter: BluetoothAdapter? = null
    private var mPairedDevicesArrayAdapter: ArrayAdapter<String>? = null
    private var mNewDevicesArrayAdapter: ArrayAdapter<String>? = null

    // Member widgets
    private var mScanButton: Button? = null
    private var mNewDivicesTitle: TextView? = null
    private var mPairedDevicesTitle: TextView? = null
    private var mPairedDevicesList: ListView? = null
    private var mNewDevicesList: ListView? = null

    /////////////////////////////////////////////////////////////////
    // The BroadcastReceiver that listens for discovered devices and
    // changes the title when discovery is finished
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object from the Intent
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // If it's already paired, skip it, because it's been listed already
                if (device != null) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        mNewDevicesArrayAdapter!!.add("""
            ${device.name}
            ${device.address}
            """.trimIndent())
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                // setProgressBarIndeterminateVisibility(false) // Deprecated
                setTitle(R.string.select_device)
                if (mNewDevicesArrayAdapter!!.count == 0) {
                    val noDevices = resources.getText(R.string.no_device_found).toString()
                    mNewDevicesArrayAdapter!!.add(noDevices)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_obd2_monitor_devices)

        // Set result CANCELED in case the user backs out
        setResult(RESULT_CANCELED)
        mNewDivicesTitle = findViewById(R.id.title_new_devices) as TextView
        mPairedDevicesTitle = findViewById(R.id.title_paired_devices) as TextView
        mScanButton = findViewById(R.id.button_scan) as Button
        mScanButton!!.setOnClickListener { view ->
            doDiscovery()
            view.visibility = View.GONE
        }

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        mPairedDevicesArrayAdapter = ArrayAdapter(this, R.layout.device_name)
        mNewDevicesArrayAdapter = ArrayAdapter(this, R.layout.device_name)

        // Find and set up the ListView for paired devices
        mPairedDevicesList = findViewById(R.id.paired_devices) as ListView
        mPairedDevicesList!!.adapter = mPairedDevicesArrayAdapter
        mPairedDevicesList!!.onItemClickListener = mDeviceClickListener

        // Find and set up the ListView for newly discovered devices
        mNewDevicesList = findViewById(R.id.new_devices) as ListView
        mNewDevicesList!!.adapter = mNewDevicesArrayAdapter
        mNewDevicesList!!.onItemClickListener = mDeviceClickListener

        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(mReceiver, filter)

        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(mReceiver, filter)

        // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter()

        // Get a set of currently paired devices
        val pairedDevices = mBtAdapter.getBondedDevices()

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size > 0) {
            mPairedDevicesTitle!!.visibility = View.VISIBLE
            for (device in pairedDevices) {
                mPairedDevicesArrayAdapter!!.add("""
    ${device.name}
    ${device.address}
    """.trimIndent())
            }
        } else {
            val noDevices = resources.getText(R.string.none_paired).toString()
            mPairedDevicesArrayAdapter!!.add(noDevices)
        }
    }

    ///////////////////////////////////////////////////////////
    private val mDeviceClickListener = AdapterView.OnItemClickListener { av, v, arg2, arg3 -> // Cancel discovery because it's costly and we're about to connect
        mBtAdapter!!.cancelDiscovery()

        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)

        // Create the result Intent and include the MAC address
        val intent = Intent()
        intent.putExtra(EXTRA_DEVICE_ADDRESS, address)

        // Set result and finish this Activity
        setResult(RESULT_OK, intent)
        finish()
    }


    private fun doDiscovery() {
        // Indicate scanning in the title
        setProgressBarIndeterminateVisibility(true)
        setTitle(R.string.device_scanning)

        // Turn on sub-title for new devices
        mNewDivicesTitle!!.visibility = View.VISIBLE

        // If we're already discovering, stop it
        if (mBtAdapter!!.isDiscovering) {
            mBtAdapter!!.cancelDiscovery()
        }

        // Request discover from BluetoothAdapter
        mBtAdapter!!.startDiscovery()
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter!!.cancelDiscovery()
        }

        // Unregister broadcast listeners
        unregisterReceiver(mReceiver)
    }


    companion object {
        private const val TAG = "DeviceListActivity"
        private const val D = true

        // Return Intent extra
        var EXTRA_DEVICE_ADDRESS = "device_address"
    }


}