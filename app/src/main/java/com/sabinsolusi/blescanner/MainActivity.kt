package com.sabinsolusi.blescanner

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sabinsolusi.blescanner.DeviceProvile.Companion.CHARACTERISTIC_STATE_UUID
import com.sabinsolusi.blescanner.DeviceProvile.Companion.SERVICE_UUID
import java.util.*


class MainActivity : AppCompatActivity(), LogsAdapter.ItemClickListener {
    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var txtStatus: TextView
    private lateinit var rcvLogs: RecyclerView

    private lateinit var logsAdapter: LogsAdapter
    
    val logs: ArrayList<String> = ArrayList()

    companion object{
        private const val TAG = "BLEScanner"
        const val BLUETOOTH_REQUEST_CODE = 1
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        rcvLogs = findViewById(R.id.rcvLogs)
        
        rcvLogs.layoutManager = LinearLayoutManager(this)
        logsAdapter = LogsAdapter(this, logs)
        logsAdapter.setClickListener(this)
        rcvLogs.adapter = logsAdapter
    }

    override fun onItemClick(view: View?, position: Int) {
        Toast.makeText(
            this,
            "You clicked " + logsAdapter.getItem(position) + " on row number " + position,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onResume() {
        super.onResume()

        if(bluetoothAdapter.isEnabled){
            startScan()
        }else{
            log(TAG, "BT Disabled")
            val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(btIntent, BLUETOOTH_REQUEST_CODE)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startScan(){
        log(TAG, "Scanning...")
        txtStatus.text = "Scanning..."

        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val scanFilter = ScanFilter.Builder().build()
        val scanFilters: MutableList<ScanFilter> = mutableListOf()
        scanFilters.add(scanFilter)

        bluetoothAdapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
    }

    private val scanCallback: ScanCallback by lazy{
        object: ScanCallback(){
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                log(TAG, "onScanResult")

                val bluetoothDevice = result?.device
                if(bluetoothDevice != null){
                   log(TAG, "Device Found: ${bluetoothDevice.name}\nAddress: ${bluetoothDevice.address}")

                    connectToDevice(bluetoothDevice)
                }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice){
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun sendMessage(){
        val service: BluetoothGattService? = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_STATE_UUID)
        val message = "DEVICE READY"

        val msgBytes = message.toByteArray(charset("UTF-8"))

        characteristic!!.value = msgBytes

        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    private val gattCallback: BluetoothGattCallback by lazy{
        object: BluetoothGattCallback(){
            @SuppressLint("SetTextI18n")
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                log(TAG, "onConnectionStateChange")

                if(newState == BluetoothProfile.STATE_CONNECTED){
                    if (gatt != null) {
                        txtStatus.text = "Connected to ${gatt.device.name} [${gatt.device.address}]"
                    }
                    bluetoothGatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                super.onServicesDiscovered(gatt, status)
                log(TAG, "onServicesDiscovered")

                val service = gatt!!.getService(SERVICE_UUID)
                val characteristic = service.getCharacteristic(CHARACTERISTIC_STATE_UUID)

                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                gatt.setCharacteristicNotification(characteristic, true)

                sendMessage()
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                super.onCharacteristicRead(gatt, characteristic, status)
                log(TAG, "onCharacteristicRead")
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicChanged(gatt, characteristic)
                log(TAG, "onCharacteristicChanged: ${characteristic?.getStringValue(0)}")
            }


        }
    }
    
    fun log(tag: String, msg: String){
        Log.e(tag, msg)
        
        logs.add(msg)
        logsAdapter.notifyItemInserted(logs.size-1)
    }
}

class DeviceProvile{
    companion object{
        val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")!! //ARDUINO SERVICE UUID
        val CHARACTERISTIC_STATE_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")!! //ARDUINO CHARACTERISTIC UUID
    }
}