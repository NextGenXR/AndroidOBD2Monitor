package com.nextgenxr.jenniobd2monitor

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class OBD2MonitorService (context: Context?, handler: Handler?){

    // Member fields
    private val mAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val mHandler: Handler
    private var mSecureAcceptThread: AcceptThread? = null
    private var mInsecureAcceptThread: AcceptThread? = null
    private var mConnectThread: ConnectThread? = null
    private var mConnectedThread: ConnectedThread? = null
    private var mState: Int = 0

    /**
     * Return the current connection state.
     * Give the new state to the Handler so the UI Activity can update
     *
     * Set the current state of the chat connection
     * @param state  An integer defining the current connection state
     */
    @get:Synchronized
    @set:Synchronized
    var state: Int
        get() = this.mState
        private set(state)
        {
            if (D) Log.d(TAG, "setState() ${this.mState} -> $state")
            this.mState = state

            // Give the new state to the Handler so the UI Activity can update
            mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_STATE_CHANGE,
                    state, -1).sendToTarget()
        }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    @Synchronized
    fun start()
    {
        if (D) Log.d(TAG, "start")

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null)
        {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null)
        {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }
        state = STATE_LISTEN

        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null)
        {
            mSecureAcceptThread = AcceptThread(true)
            mSecureAcceptThread!!.start()
        }

        if (mInsecureAcceptThread == null)
        {
            mInsecureAcceptThread = AcceptThread(false)
            mInsecureAcceptThread!!.start()
        }
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     * @param device  The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    @Synchronized
    fun connect(device: BluetoothDevice, secure: Boolean)
    {
        if (D) Log.d(TAG, "connect to: $device")

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING)
        {
            if (mConnectThread != null)
            {
                mConnectThread!!.cancel()
                mConnectThread = null
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null)
        {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Start the thread to connect with the given device
        mConnectThread = ConnectThread(device, secure)
        mConnectThread!!.start()
        state = STATE_CONNECTING
    }


    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     * @param socket  The BluetoothSocket on which the connection was made
     * @param device  The BluetoothDevice that has been connected
     */
    @Synchronized
    fun connected(socket: BluetoothSocket?, device: BluetoothDevice, socketType: String) {
        if (D) Log.d(TAG, "connected, Socket Type:$socketType")

        // Cancel the thread that completed the connection
        if (mConnectThread != null)
        {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null)
        {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null)
        {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }

        if (mInsecureAcceptThread != null)
        {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = ConnectedThread(socket, socketType)
        mConnectedThread!!.start()

        // Send the name of the connected device back to the UI Activity
        val msg: Message = mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_DEVICE_NAME)
        val bundle = Bundle()
        bundle.putString(OBD2MonitorMainActivity.DEVICE_NAME, device.getName())
        msg.data = bundle
        mHandler.sendMessage(msg)
        state = STATE_CONNECTED
    }


    /**
     * Stop all threads
     */
    @Synchronized
    fun stop()
    {
        if (D) Log.d(TAG, "stop")
        if (mConnectThread != null)
        {
            mConnectThread!!.cancel()
            mConnectThread = null
        }

        if (mConnectedThread != null)
        {
            mConnectedThread!!.cancel()
            mConnectedThread = null
        }

        if (mSecureAcceptThread != null)
        {
            mSecureAcceptThread!!.cancel()
            mSecureAcceptThread = null
        }

        if (mInsecureAcceptThread != null)
        {
            mInsecureAcceptThread!!.cancel()
            mInsecureAcceptThread = null
        }

        state = STATE_NONE
    }


    /**
     * Write to the ConnectedThread in an unsynchronized manner
     * @param out The bytes to write
     * @see ConnectedThread.write
     */
    fun write(out: ByteArray?)
    {
        // Create temporary object
        var writeThread: ConnectedThread?

        // Synchronize a copy of the ConnectedThread
        synchronized(this)
        {
            if (mState != STATE_CONNECTED) return
            writeThread = mConnectedThread
        }

        // Perform the write unsynchronized
        writeThread!!.write(out)
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private fun connectionFailed()
    {
        // Send a failure message back to the Activity
        val msg: Message = mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(OBD2MonitorMainActivity.TOAST, "Unable to connect device")
        msg.data = bundle
        mHandler.sendMessage(msg)

        // Start the service over to restart listening mode
        start()
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private fun connectionLost()
    {
        // Send a failure message back to the Activity
        val msg: Message = mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_TOAST)
        val bundle = Bundle()
        bundle.putString(OBD2MonitorMainActivity.TOAST,
            "Device connection was lost")
        msg.data = bundle
        mHandler.sendMessage(msg)

        // Start the service over to restart listening mode
        start()
    }


    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private inner class AcceptThread(secure: Boolean) : Thread() {
        // The local server socket
        private val mmServerSocket: BluetoothServerSocket?
        private val mSocketType: String

        override fun run()
        {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType +
                    "BEGIN mAcceptThread" + this)

            name = "AcceptThread$mSocketType"
            var socket: BluetoothSocket? = null

            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                if (mmServerSocket != null) {
                    socket = try {
                        // This is a blocking call and will only return on a
                        // successful connection or an exception
                        mmServerSocket.accept()
                    } catch (e: IOException) {
                        Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e)
                        break
                    }
                }

                // If a connection was accepted
                if (socket != null)
                {
                    synchronized(this@OBD2MonitorService)
                    {
                        when (mState)
                        {
                            STATE_LISTEN, STATE_CONNECTING ->                                 // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                    mSocketType)
                            STATE_NONE, STATE_CONNECTED ->                                 // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            else -> {}
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: $mSocketType")
        }

        fun cancel()
        {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this)
            try
            {
                mmServerSocket.close()
            } catch (e: IOException)
            {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e)
            }
        }

        init
        {
            var tmp: BluetoothServerSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Create a new listening server socket
            try
            {
                tmp = if (secure)
                {
                    mAdapter.listenUsingRfcommWithServiceRecord(
                        NAME_SECURE, MY_UUID_SECURE)
                } else {
                    mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        NAME_INSECURE, MY_UUID_INSECURE)
                }
            } catch (e: IOException)
            {
                Log.e(TAG, "Socket Type: " + mSocketType +
                        "listen() failed", e)
            }
            mmServerSocket = tmp
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private inner class ConnectThread(device: BluetoothDevice, secure: Boolean) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mmDevice: BluetoothDevice = device
        private val mSocketType: String

        override fun run()
        {
            Log.i(TAG, "BEGIN mConnectThread SocketType:$mSocketType")
            name = "ConnectThread$mSocketType"

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery()

            // Make a connection to the BluetoothSocket
            try
            {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect()
            } catch (e: IOException)
            {
                // Close the socket
                e.printStackTrace()
                try
                {
                    mmSocket.close()
                } catch (e2: IOException)
                {
                    Log.e(TAG, "unable to connect() " + mSocketType +
                            " socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@OBD2MonitorService) { mConnectThread = null }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType)
        }

        fun cancel()
        {
            try
            {
                mmSocket.close()
            } catch (e: IOException)
            {
                Log.e(TAG, "close() of connect $mSocketType socket failed", e)
            }
        }

        init
        {
            var tmp: BluetoothSocket? = null
            mSocketType = if (secure) "Secure" else "Insecure"

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                tmp = if (secure)
                {
                    device.createRfcommSocketToServiceRecord(
                        MY_UUID_SECURE)
                } else
                {
                    device.createInsecureRfcommSocketToServiceRecord(
                        MY_UUID_INSECURE)
                }
            } catch (e: IOException)
            {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e)
            }
            mmSocket = tmp
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private inner class ConnectedThread(socket: BluetoothSocket?, socketType: String) : Thread() {
        private val mmSocket: BluetoothSocket?
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        override fun run()
        {
            Log.i(TAG, "BEGIN mConnectedThread")
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream while connected
            while (true)
            {
                try
                {
                    // Read from the InputStream for test
                    bytes = mmInStream.read(buffer)
                    //                    bytes = read(buffer);
                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_READ,
                        bytes, -1, buffer).sendToTarget()
                } catch (e: IOException)
                {
                    Log.e(TAG, "disconnected", e)
                    connectionLost()
                    // Start the service over to restart listening mode
                    start()
                    break
                }
            }
        }

        /**
         * Read  data from the connected InputStream
         * @param buffer The bytes to read
         */
        @Throws(IOException::class)
        private fun read(buffer: ByteArray): Int
        {
            var bytes = 0
            var escape = false
            var prompt = false

            val readBuf: ByteArray = ByteArray(1)

            while (!escape)
            {
                bytes += mmInStream.read(readBuf)
                val s = String(readBuf)
                if (s != null && !s.equals(""))
                {
                    buffer[bytes] = readBuf[0]
                    prompt = s.indexOf(">") >= 0
                }
                escape = prompt
            }
            return bytes
        }

        /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        fun write(buffer: ByteArray?)
        {
            try
            {
                mmOutStream.write(buffer)

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(OBD2MonitorMainActivity.MESSAGE_WRITE, -1, -1, buffer)
                    .sendToTarget()
            } catch (e: IOException)
            {
                Log.e(TAG, "Exception during write", e)
            }
        }

        fun cancel()
        {
            try
            {
                mmInStream.close()
                mmOutStream.close()
                mmSocket.close()
            } catch (e: IOException)
            {
                Log.e(TAG, "close() of connect socket failed", e)
            }
        }

        init
        {
            Log.d(TAG, "create ConnectedThread: $socketType")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try
            {
                tmpIn = socket.getInputStream()
                tmpOut = socket.getOutputStream()
            } catch (e: IOException)
            {
                Log.e(TAG, "temp sockets not created", e)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }

    companion object
    {
        // Debugging
        private const val TAG = "OBD2MonitorService"
        private const val D = true

        // Name for the SDP record when creating server socket
        private const val NAME_SECURE = "OBD2MonitorSecure"
        private const val NAME_INSECURE = "OBD2MonitorInsecure"

        // Unique UUID for this application
        //    private static final UUID MY_UUID_SECURE =
        //        UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
        //    private static final UUID MY_UUID_INSECURE =
        //        UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
        private val MY_UUID_SECURE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private val MY_UUID_INSECURE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_LISTEN = 1 // now listening for incoming connections
        const val STATE_CONNECTING = 2 // now initiating an outgoing connection
        const val STATE_CONNECTED = 3 // now connected to a remote device
    }

    /**
     * Constructor. Prepares a new BluetoothChat session.
     * @param context  The UI Activity Context
     * @param handler  A Handler to send messages back to the UI Activity
     */
    init
    {
        mState = STATE_NONE
        if (handler != null) {
            mHandler = handler
        }
    }







    }