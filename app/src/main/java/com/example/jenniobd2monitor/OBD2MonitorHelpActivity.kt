package com.nextgenxr.jenniobd2monitor

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.GridView
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class OBD2MonitorHelpActivity : AppCompatActivity()
{
    private var mOBD2Protocols: ListView? = null
    private var mOBD2Commands: GridView? = null

    override fun onCreate(savaInstanceState: Bundle?)
    {
        super.onCreate(savaInstanceState)
        setContentView(R.layout.activity_obd2_monitor_help)
        mOBD2Protocols = findViewById<ListView>(R.id.obd2_protocol_listView)
        val mItems = resources.getStringArray(R.array.protocols)

        // Create a Adapter for the mOBD2Protocols(ListView) and then bind the datasource
        val listAdapter = ArrayAdapter(this, R.layout.device_name, mItems)

        //Sets the data behind this mOBD2Protocols(ListView)
        mOBD2Protocols!!.adapter = listAdapter
        mOBD2Commands = findViewById<GridView>(R.id.obd2_cmd_gridView)
        val mCmdItmes = resources.getStringArray(R.array.OBD2_Commands)
        val gridAdapter = ArrayAdapter(this, R.layout.grid_name, mCmdItmes)
        mOBD2Commands!!.adapter = gridAdapter
    }

}