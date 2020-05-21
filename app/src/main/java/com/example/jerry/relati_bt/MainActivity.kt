package com.example.jerry.relati_bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.service.autofill.Validators.not
import android.util.Log
import android.view.View
import android.widget.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.lang.reflect.Method
import java.util.*

private lateinit var mHandler: Handler

val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")  // "random" unique identifier
val REQUEST_ENABLE_BT = 1   // used to identify adding bluetooth names
val MESSAGE_READ = 2    // used in bluetooth handler to identify message update
val CONNECTING_STATUS = 3   // used in bluetooth handler to identify message status

val mDevice: BluetoothDevice? = null

class MainActivity : AppCompatActivity() {
    private lateinit var mPairedDevices:Set<BluetoothDevice>
    private lateinit var mBTArrayAdapter: ArrayAdapter<String>
    private lateinit var mBTAdapter: BluetoothAdapter

    private lateinit var mDevicesListView: ListView


    private var mConnectedThread: ConnectedThread? = null

    private val TAG = MainActivity::class.java.simpleName
    private lateinit var mBTSocket: BluetoothSocket // bi-directional client-to-client data path

    private var LED1_status = false
    private var LED2_status = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mBTArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        mBTAdapter = BluetoothAdapter.getDefaultAdapter() // get a handle on the bluetooth radio
        mDevicesListView = findViewById<ListView>(R.id.devicesListView)
        mDevicesListView.adapter = mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener

        initBluetooth()
    }

    private fun initBluetooth() {
        mHandler = @SuppressLint("HandlerLeak")
        object:Handler() {
            override fun handleMessage(msg:android.os.Message) {
                if (msg.what === MESSAGE_READ) {
                    var readMessage: String? = null

                    try {
                        readMessage = String(msg.obj as ByteArray, Charsets.UTF_8)
                    }
                    catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }
                    readBuffer.text = readMessage
                }
                if (msg.what === CONNECTING_STATUS) {
                    if (msg.arg1 === 1)
                        bluetoothStatus.text = "Connected to Device: " + (msg.obj) as String
                    else
                        bluetoothStatus.text = "Connection Failed"
                }
            }
        }

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            bluetoothStatus.text = "Status: Bluetooth not found"
            Toast.makeText(applicationContext,"Bluetooth device not found!",Toast.LENGTH_SHORT).show()
        } else {
            checkboxLED1.setOnClickListener {
                // Test LED1
                if (!LED1_status)
                    Log.i("LED_status1", "A1")
                else
                    Log.i("LED_status1", "A0")

                if (mConnectedThread != null) {
                    //First check to make sure thread created
                    if (!LED1_status)
                        mConnectedThread!!.write("A1")
                    else
                        mConnectedThread!!.write("A0")
                }
                LED1_status = !LED1_status
            }

            checkboxLED2.setOnClickListener {
                // Test LED2
                if (!LED2_status)
                    Log.i("LED_status2", "B1")
                else
                    Log.i("LED_status2", "B0")
                if (mConnectedThread != null) {
                    //First check to make sure thread created
                    if (!LED2_status)
                        mConnectedThread!!.write("B1")
                    else
                        mConnectedThread!!.write("B0")
                }
                LED2_status = !LED2_status
            }

            BTscan.setOnClickListener { v -> v?.let { bluetoothOn() } }

            BToff.setOnClickListener { v -> v?.let { bluetoothOff() } }

            BTpaired.setOnClickListener { v -> v?.let { listPairedDevices() } }

            BTdiscover.setOnClickListener { v -> v?.let { discover() } }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, Data: Intent?) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT)
        {
            // Make sure the request was successful
            if (resultCode == RESULT_OK)
            {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                bluetoothStatus.text = "Bluetooth 啟動"
            }
            else
                bluetoothStatus.text = "Bluetooth 關閉"
        }
    }

    private fun bluetoothOn() {
        if (!mBTAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            bluetoothStatus.text = "Bluetooth 啟動"
            Toast.makeText(applicationContext, "Bluetooth turned on", Toast.LENGTH_SHORT).show()
        } else {
            bluetoothStatus.text = "Bluetooth 啟動"
            Toast.makeText(applicationContext, "Bluetooth is already on", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bluetoothOff() {
        mBTAdapter.disable() // turn off
        bluetoothStatus.text = "Bluetooth 關閉"
        Toast.makeText(applicationContext, "Bluetooth turned Off", Toast.LENGTH_SHORT).show()
    }

    private fun listPairedDevices() {
        mBTArrayAdapter.clear()
        mPairedDevices = mBTAdapter.bondedDevices
        Log.e("MyPairedDevices", mPairedDevices.toString())

        if (mBTAdapter.isEnabled) {
            // put it's one to the adapter
            for (device in mPairedDevices) {
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress())
            }
            Toast.makeText(applicationContext, "Show Paired Devices", Toast.LENGTH_SHORT).show()
        }
        else
            Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
    }

    private fun discover() {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering) {
            mBTAdapter.cancelDiscovery()
            Toast.makeText(applicationContext, "Discovery stopped", Toast.LENGTH_SHORT).show()
        }
        else {
            if (mBTAdapter.isEnabled) {
                mBTArrayAdapter.clear() // clear items
                mBTAdapter.startDiscovery()
                Toast.makeText(applicationContext, "Discovery started", Toast.LENGTH_SHORT).show()
                registerReceiver(blReceiver,  IntentFilter(BluetoothDevice.ACTION_FOUND))
            }
            else {
                Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val blReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.e("MyBTAction1", action)
            if (BluetoothDevice.ACTION_FOUND == action) {
                Log.e("MyBTAction2", BluetoothDevice.ACTION_FOUND)
                val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                // add the name to the list
                mBTArrayAdapter.add(device.name + "\n" + device.address)
                mBTArrayAdapter.notifyDataSetChanged()
            }
        }
    }

    private val mDeviceClickListener = AdapterView.OnItemClickListener { av, v, arg2, arg3 ->
        if (!mBTAdapter.isEnabled) {
            Toast.makeText(baseContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            return@OnItemClickListener
        }
        bluetoothStatus.text = "Connecting..."
        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)
        val name = info.substring(0, info.length - 17)
        // Spawn a new thread to avoid blocking the GUI one
        object : Thread() {
            override fun run() {
                var fail = false
                val device: BluetoothDevice = mBTAdapter.getRemoteDevice(address)
                try {
                    mBTSocket = createBluetoothSocket(device)
                }
                catch (e:IOException) {
                    fail = true
                    Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
                }
                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket!!.connect()
                }
                catch (e:IOException) {
                    try {
                        fail = true
                        mBTSocket!!.close()
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget()
                    }
                    catch (e2:IOException) {
                        //insert code to deal with this
                        Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
                    }
                }
                if (!fail) {
//                    mConnectedThread = mBTSocket?.let { ConnectedThread(it) }!!
                    mConnectedThread = ConnectedThread(mBTSocket)
                    mConnectedThread!!.start()
                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget()
                }
            }
        }.start()
    }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice) : BluetoothSocket {
        try {
            val m: Method = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return m.invoke(device, BTMODULEUUID) as BluetoothSocket
        }
        catch (e: Exception) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e)
        }
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID)
    }

    private class ConnectedThread(socket: BluetoothSocket) : Thread() {
        private val mmSocket: BluetoothSocket = socket
        private val mmInStream: InputStream
        private val mmOutStream: OutputStream
        init {
            var tmpIn: InputStream? = null
            var tmpOut:OutputStream? = null
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            }
            catch (e: IOException) {}
            mmInStream = tmpIn!!
            mmOutStream = tmpOut!!
        }

        override fun run() {
            var buffer: ByteArray // buffer store for the stream
            var bytes: Int // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available()
                    if (bytes != 0) {
                        buffer = ByteArray(1024)
                        SystemClock.sleep(100) //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available() // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes) // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget() // Send the obtained bytes to the UI activity
                    }
                }
                catch (e:IOException) {
                    e.printStackTrace()
                    break
                }
            }
        }
        /* Call this from the main activity to send data to the remote device */
        fun write(input:String) {
            val bytes = input.toByteArray() //converts entered String into bytes
//            try {
//                mmOutStream.write(bytes)
//            }
//            catch (e:IOException) {}
            Log.i("LED_status", "LED1: $input,  LED2: $input")
        }
        /* Call this from the main activity to shutdown the connection */
        fun cancel() {
            try {
                mmSocket.close()
            }
            catch (e:IOException) {}
        }
    }
}