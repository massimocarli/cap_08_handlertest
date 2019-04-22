package uk.co.massimocarli.handlertest

import android.graphics.drawable.ClipDrawable
import android.os.*
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import uk.co.massimocarli.handlertest.MainActivity.ConsumerThread.Companion.CONSUMER_WHAT
import uk.co.massimocarli.handlertest.MainActivity.UpdateUiHandler.Companion.CLIP_UPDATE_WHAT
import java.lang.ref.WeakReference
import java.util.function.Consumer


typealias CounterCallback = Consumer<Int>

class MainActivity : AppCompatActivity() {

  lateinit var counterThread: CounterThread

  lateinit var updateUiHandler: Handler
  lateinit var consumerHandler: Handler
  lateinit var consumerThread: ConsumerThread
  lateinit var handlerThread: HandlerThread


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    // This is the case of direct using of the CounterThread
    updateUiHandler = UpdateUiHandler(Consumer {
      updateDirectly(it)
    })
    consumerThread = ConsumerThread(Handler.Callback {
      if (it.what == CONSUMER_WHAT) {
        Log.d("CONSUMER_THREAD", "Received: ${it.obj}")
        true
      } else {
        false
      }
    }).apply {
      start()
    }
    counterThread = CounterThread(CounterCallback {
      // Update the ClipDrawable directly
      //updateDirectly(it)
      // This is the case of using CounterThread with an Handler
      updateWithHandler(it)
      sendToConsumer(it)
      sendToConsumerHandler(it)
    })
    handlerThread = HandlerThread("HandlerThreadConsumer").apply {
      start()
    }
    consumerHandler = object : Handler(handlerThread.looper) {
      override fun handleMessage(msg: Message?) {
        if (msg?.what == CONSUMER_WHAT) {
          Log.d("CONSUMER_THREAD", "Received: ${msg.obj}")
          true
        } else {
          false
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    handlerThread.quitSafely()
    consumerThread.stopHandler()
  }

  private fun sendToConsumer(progress: Int) {
    consumerThread.consumerHandler?.let {
      val msg = updateUiHandler.obtainMessage(CONSUMER_WHAT, progress)
      it.sendMessage(msg)
    }
  }

  private fun sendToConsumerHandler(progress: Int) {
    consumerHandler?.let {
      val msg = consumerHandler.obtainMessage(CONSUMER_WHAT, progress)
      it.sendMessage(msg)
    }
  }

  /**
   * Contains the logic of sending a message to the Handler
   */
  private fun updateWithHandler(progress: Int) {
    updateUiHandler.sendMessage(updateUiHandler.obtainMessage(CLIP_UPDATE_WHAT)
      .apply {
        arg1 = progress
      })
  }

  private fun updateDirectly(progress: Int) {
    progress_view.background = (progress_view.background as ClipDrawable)
      .apply {
        level = progress
        progress_view.background = this
      }
  }


  private fun createHandlerThread() {
    val handlerThread = HandlerThread("HandlerThreadConsumer")
    handlerThread.start()
    object : Handler(handlerThread.looper) {

      override fun handleMessage(msg: Message?) {
        if (msg?.what == CONSUMER_WHAT) {
          Log.d("CONSUMER_THREAD", "Received: ${msg.obj}")
          true
        } else {
          false
        }
      }
    }
  }

  fun buttonPressed(pressedButton: View) {
    when (pressedButton.getId()) {
      R.id.start_button -> counterThread.start()
      R.id.stop_button -> counterThread.stop()
      R.id.reset_button -> counterThread.reset()
    }
  }

  class CounterThread(
    callback: CounterCallback? = null
  ) : Runnable {

    private var callbackRef: WeakReference<CounterCallback>? = null
    private var counter: Int = 0
    @Volatile
    private var running: Boolean = false
    private var thread: Thread? = null

    init {
      callback?.let {
        callbackRef = WeakReference<CounterCallback>(it)
      }
    }

    fun start() {
      if (!running) {
        running = true
        thread = Thread(this).apply {
          start()
        }
      }
    }

    fun stop() {
      if (running) {
        running = false
        thread = null
      }
    }

    fun reset() {
      if (!running) {
        counter = 0
        callbackRef?.get()?.accept(counter)
      }
    }

    override fun run() {
      while (running && counter < 10000) {
        Thread.sleep(5)
        callbackRef?.get()?.accept(counter++)
      }
      running = false
    }
  }

  class UpdateUiHandler(callback: CounterCallback? = null) : Handler() {

    companion object {
      const val CLIP_UPDATE_WHAT = 1
    }

    private var callbackRef: WeakReference<CounterCallback>? = null

    init {
      callback?.let {
        callbackRef = WeakReference<CounterCallback>(it)
      }
    }

    override fun handleMessage(msg: Message?) {
      super.handleMessage(msg)
      if (msg?.what == CLIP_UPDATE_WHAT) {
        callbackRef?.get()?.accept(msg.arg1)
      }
    }
  }


  class ConsumerThread(
    val callback: Handler.Callback
  ) : Thread("ConsumerThread") {

    companion object {
      const val CONSUMER_WHAT = 2
    }

    var consumerHandler: Handler? = null

    override fun run() {
      Looper.prepare()
      consumerHandler = Handler(callback)
      Looper.loop()
    }

    fun stopHandler() {
      consumerHandler?.post {
        Looper.myLooper()?.quit()
      }
    }
  }


}




