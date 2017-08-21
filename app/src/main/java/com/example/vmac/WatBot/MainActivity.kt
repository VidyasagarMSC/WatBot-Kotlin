package com.example.vmac.WatBot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.Image
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse
import com.ibm.watson.developer_cloud.http.HttpMediaType
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice

import java.util.ArrayList
import java.util.HashMap


class MainActivity : AppCompatActivity() {


    private var recyclerView: RecyclerView? = null
    private var mAdapter: ChatAdapter? = null
    private var messageArrayList: ArrayList<Message>? = null
    private var inputMessage: EditText? = null
    private var btnSend: ImageButton? = null
    private var btnRecord: ImageButton? = null
    private var context: MutableMap<String, Any> = HashMap()
    internal var streamPlayer: StreamPlayer = StreamPlayer()
    private var initialRequest: Boolean = false
    private var permissionToRecordAccepted = false
    private var listening = false
    private var speechService: SpeechToText? = null
    private var capture: MicrophoneInputStream? = null
    private val recoTokens: SpeakerLabelsDiarization.RecoTokens? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputMessage = findViewById<EditText>(R.id.message) as EditText
        btnSend = findViewById<ImageButton>(R.id.btn_send) as ImageButton
        btnRecord = findViewById<ImageButton>(R.id.btn_record) as ImageButton
        val customFont = "Montserrat-Regular.ttf"
        val typeface = Typeface.createFromAsset(assets, customFont)
        inputMessage!!.typeface = typeface
        recyclerView = findViewById<RecyclerView>(R.id.recycler_view) as RecyclerView

        messageArrayList = ArrayList()
        mAdapter = ChatAdapter(messageArrayList as ArrayList<Message>)

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        recyclerView!!.layoutManager = layoutManager
        recyclerView!!.itemAnimator = DefaultItemAnimator() as RecyclerView.ItemAnimator?
        recyclerView!!.adapter = mAdapter
        this.inputMessage!!.setText("")
        this.initialRequest = true
        sendMessage()

        //Watson Text-to-Speech Service on Bluemix
        val service = TextToSpeech()
        service.setUsernameAndPassword("<Watson Text to Speech username>", "<Watson Text to Speech password>")

        val permission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission to record denied")
            makeRequest()
        }


        recyclerView!!.addOnItemTouchListener(RecyclerTouchListener(applicationContext, recyclerView as RecyclerView, object : ClickListener {
            override fun onClick(view: View, position: Int) {
                val thread = Thread(Runnable {
                    val audioMessage: Message?
                    try {

                        audioMessage = messageArrayList!![position]
                        streamPlayer = StreamPlayer()
                        if (audioMessage.message.length>0)
                        //Change the Voice format and choose from the available choices
                            streamPlayer.playStream(service.synthesize(audioMessage.message, Voice.EN_LISA).execute())
                        else
                            streamPlayer.playStream(service.synthesize("No Text Specified", Voice.EN_LISA).execute())

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                })
                thread.start()
            }

            override fun onLongClick(view: View, position: Int) {
                recordMessage()

            }
        }))

        btnSend!!.setOnClickListener {
            if (checkInternetConnection()) {
                sendMessage()
            }
        }

        btnRecord!!.setOnClickListener { recordMessage() }
    }

    // Speech to Text Record Audio permission
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            RECORD_REQUEST_CODE -> {

                if (grantResults.size == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user")
                } else {
                    Log.i(TAG, "Permission has been granted by user")
                }
                return
            }
        }
        //if (!permissionToRecordAccepted) finish()

    }

    protected fun makeRequest() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_REQUEST_CODE)
    }

    // Sending a message to Watson Conversation Service
    private fun sendMessage() {

        val inputmessage = this.inputMessage!!.text.toString();//.text.toString() { it <= ' ' }
        if (!this.initialRequest) {
            val inputMessage = Message()
            inputMessage.message = inputmessage
            inputMessage.id = "1"
            messageArrayList!!.add(inputMessage)
        } else {
            val inputMessage = Message()
            inputMessage.message = inputmessage
            inputMessage.id = "100"
            this.initialRequest = false
            Toast.makeText(applicationContext, "Tap on the message for Voice", Toast.LENGTH_LONG).show()

        }

        this.inputMessage!!.setText("")
        mAdapter!!.notifyDataSetChanged()

        val thread = Thread(Runnable {
            try {

                val service = ConversationService(ConversationService.VERSION_DATE_2017_02_03)
                service.setUsernameAndPassword("Your Watson service UserName", "Your watson service PassWord")
                val newMessage = MessageRequest.Builder().inputText(inputmessage).context(context).build()
                val response = service.message("Your Workspace Id", newMessage).execute()

                //Passing Context of last conversation
                if (response!!.context != null) {
                    context.clear()
                    context = response.context

                }
                val outMessage = Message()
                    if (response.output != null && response.output.containsKey("text")) {
                        val responseList = response.output["text"] as ArrayList<*>
                        if (responseList.size > 0) {
                            outMessage.message = responseList[0] as String
                            outMessage.id = "2"
                        }
                        messageArrayList!!.add(outMessage)
                    }

                    runOnUiThread {
                        mAdapter!!.notifyDataSetChanged()
                        if (mAdapter!!.itemCount > 1) {
                            recyclerView!!.layoutManager.smoothScrollToPosition(recyclerView, null, mAdapter!!.itemCount - 1)

                        }
                    }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        })

        thread.start()

    }

    //Record a message via Watson Speech to Text
    private fun recordMessage() {
        //mic.setEnabled(false);
        speechService = SpeechToText()
        speechService!!.setUsernameAndPassword("<Watson Speech to Text username>", "<Watson Speech to Text password>")

        if (listening != true) {
            capture = MicrophoneInputStream(true)
            Thread(Runnable {
                try {
                    speechService!!.recognizeUsingWebSocket(capture!!, recognizeOptions, MicrophoneRecognizeDelegate())
                } catch (e: Exception) {
                    showError(e)
                }
            }).start()
            listening = true
            Toast.makeText(this@MainActivity, "Listening....Click to Stop", Toast.LENGTH_LONG).show()

        } else {
            try {
                capture!!.close()
                listening = false
                Toast.makeText(this@MainActivity, "Stopped Listening....Click to Start", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    /**
     * Check Internet Connection
     * @return
     */
    private fun checkInternetConnection(): Boolean {
        // get Connectivity Manager object to check connection
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = cm.activeNetworkInfo
        val isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting

        // Check for network connections
        if (isConnected) {
            return true
        } else {
            Toast.makeText(this, " No Internet Connection available ", Toast.LENGTH_LONG).show()
            return false
        }

    }

    //Private Methods - Speech to Text
    private //.model("en-UK_NarrowbandModel")
            //TODO: Uncomment this to enable Speaker Diarization
            //.speakerLabels(true)
    val recognizeOptions: RecognizeOptions
        get() = RecognizeOptions.Builder()
                .continuous(true)
                .contentType(ContentType.OPUS.toString())
                .interimResults(true)
                .inactivityTimeout(2000)
                .build()

    private inner class MicrophoneRecognizeDelegate : RecognizeCallback {

        override fun onTranscription(speechResults: SpeechResults) {
            //println(speechResults)
            //TODO: Uncomment this to enable Speaker Diarization
            /*recoTokens = new SpeakerLabelsDiarization.RecoTokens();
            if(speechResults.getSpeakerLabels() !=null)
            {
                recoTokens.add(speechResults);
                Log.i("SPEECHRESULTS",speechResults.getSpeakerLabels().get(0).toString());


            }*/
            if (speechResults.results != null && !speechResults.results.isEmpty()) {
                val text = speechResults.results[0].alternatives[0].transcript
                showMicText(text)
            }
        }

        override fun onConnected() {

        }

        override fun onError(e: Exception) {
            showError(e)
            enableMicButton()
        }

        override fun onDisconnected() {
            enableMicButton()
        }

        override fun onInactivityTimeout(runtimeException: RuntimeException) {

        }

        override fun onListening() {

        }

        override fun onTranscriptionComplete() {

        }
    }

    private fun showMicText(text: String) {
        runOnUiThread { inputMessage!!.setText(text) }
    }

    private fun enableMicButton() {
        runOnUiThread { btnRecord!!.isEnabled = true }
    }

    private fun showError(e: Exception) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    companion object {
        private val REQUEST_RECORD_AUDIO_PERMISSION = 200
        private val TAG = "MainActivity"
        private val RECORD_REQUEST_CODE = 101
    }

}

