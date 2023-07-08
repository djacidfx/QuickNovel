package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.quicknovel.databinding.ReadMainBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import com.lagradost.quicknovel.ui.OrientationType
import com.lagradost.quicknovel.ui.ScrollVisibility
import com.lagradost.quicknovel.ui.ScrollVisibilityItem
import com.lagradost.quicknovel.ui.TextAdapter
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.toPx
import java.lang.Integer.max
import java.lang.Integer.min
import java.lang.ref.WeakReference

class ReadActivity2 : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        private var _readActivity: WeakReference<ReadActivity2>? = null
        var readActivity
            get() = _readActivity?.get()
            private set(value) {
                _readActivity = WeakReference(value)
            }
    }

    private fun hideSystemUI() {

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.readerContainer).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        fun lowerBottomNav(v: View) {
            v.translationY = 0f
            ObjectAnimator.ofFloat(v, "translationY", v.height.toFloat()).apply {
                duration = 200
                start()
            }.doOnEnd {
                v.isVisible = false
            }
        }

        lowerBottomNav(binding.readerBottomViewHolder)

        binding.readToolbarHolder.translationY = 0f
        ObjectAnimator.ofFloat(
            binding.readToolbarHolder,
            "translationY",
            -binding.readToolbarHolder.height.toFloat()
        ).apply {
            duration = 200
            start()
        }.doOnEnd {
            binding.readToolbarHolder.isVisible = false
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(
            window,
            binding.readerContainer
        ).show(WindowInsetsCompat.Type.systemBars())

        binding.readToolbarHolder.isVisible = true

        fun higherBottomNavView(v: View) {
            v.isVisible = true
            v.translationY = v.height.toFloat()
            ObjectAnimator.ofFloat(v, "translationY", 0f).apply {
                duration = 200
                start()
            }
        }

        higherBottomNavView(binding.readerBottomViewHolder)

        binding.readToolbarHolder.translationY = -binding.readToolbarHolder.height.toFloat()

        ObjectAnimator.ofFloat(binding.readToolbarHolder, "translationY", 0f).apply {
            duration = 200
            start()
        }
    }

    lateinit var binding: ReadMainBinding
    private val viewModel: ReadActivityViewModel by viewModels()

    override fun onColorSelected(dialog: Int, color: Int) {
        when (dialog) {
            0 -> setBackgroundColor(color)
            1 -> setTextColor(color)
        }
    }

    private fun setBackgroundColor(color: Int) {

    }

    private fun setTextColor(color: Int) {

    }

    override fun onDialogDismissed(dialog: Int) {
        updateImages()
    }

    private fun updateImages() {

    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            kill()
            return true
        }
        if ((keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) return false

        // if we have the bottom bar up then we ignore the override functionality
        if (viewModel.bottomVisibility.isInitialized && viewModel.bottomVisibility.value == true) return false

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.forwardsTTS()
                    return true
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.backwardsTTS()
                    return true
                }
            }
        }

        return false
    }

    private fun kill() {
        with(NotificationManagerCompat.from(this)) { // KILLS NOTIFICATION
            cancel(TTS_NOTIFICATION_ID)
        }
        finish()
    }

    private fun registerBattery() {
        val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent) {
                val batteryPct: Float = run {
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level * 100 / scale.toFloat()
                }
                binding.readBattery.text =
                    getString(R.string.battery_format).format(batteryPct.toInt())
            }
        }
        this.registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun parseAction(input: TTSHelper.TTSActionType): Boolean {
        return viewModel.parseAction(input)
    }

    private lateinit var textAdapter: TextAdapter
    private lateinit var textLayoutManager: LinearLayoutManager

    private fun transformIndexToScrollVisibilityItem(adapterPosition: Int): ScrollVisibilityItem {
        return ScrollVisibilityItem(
            adapterPosition = adapterPosition,
            viewHolder = binding.realText.findViewHolderForAdapterPosition(adapterPosition),
        )
    }

    private fun getTopY(): Int {
        val outLocation = IntArray(2)
        binding.readTopItem.getLocationInWindow(outLocation)
        val (_, topY) = outLocation
        return topY
    }

    private fun getBottomY(): Int {
        val outLocation = IntArray(2)
        binding.readBottomItem.getLocationInWindow(outLocation)
        val (_, bottomY) = outLocation
        return bottomY
    }

    /** this works by first getting the 4 interesting items, first, firstVisible, last, lastVisible
    then computing the first visible and first invisible char

    ________________
    [ hello ]
    -- screen cut --
    [ world ]
    [ ! ]
    [ From kotlin ]
    ________________

    here the first index of "world" would be stored as it is the first whole line visible,
    while "hello" would be stored as the first invisible line, this is used to scroll the exact char
    you are on. so while rotating you would rotate to the first line that contains "world"

    This is also used for TTS because TTS *must* start at the first visible whole sentence,
    so in this case it would start at "From kotlin" because hello is not visible.

    The struct returned has 2 index variables, index = chapter, while innerIndex = what item in that
    chapter. So in this case TTS would start at index = N, innerIndex = 0, firstVisibleChar = 16 (F)
    because firstVisibleChar is relative to the textview, not the entire text
     */
    fun onScroll() {
        val topY = getTopY()
        val bottomY = getBottomY()

        val visibility = ScrollVisibility(
            firstVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findFirstVisibleItemPosition()),
            firstFullyVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findFirstCompletelyVisibleItemPosition()),
            lastVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findLastVisibleItemPosition()),
            lastFullyVisible = transformIndexToScrollVisibilityItem(textLayoutManager.findLastCompletelyVisibleItemPosition()),
            screenTop = topY,
            screenBottom = bottomY,
            screenTopBar = binding.readToolbarHolder.height
        )

        viewModel.onScroll(textAdapter.getIndex(visibility))
    }

    private var cachedChapter: List<SpanDisplay> = emptyList()
    private fun scrollToDesired() {
        val desired = viewModel.desiredIndex
        val adapterPosition =
            cachedChapter.indexOfFirst { display -> display.index == desired.index && display.innerIndex == desired.innerIndex }
        if (adapterPosition > 0) {
            val offset = 7.toPx
            textLayoutManager.scrollToPositionWithOffset(adapterPosition, offset)
            desired.firstVisibleChar?.let { visible ->
                binding.realText.post {
                    binding.realText.scrollBy(
                        0,
                        (textAdapter.getViewOffset(
                            transformIndexToScrollVisibilityItem(adapterPosition),
                            visible
                        ) ?: 0) + offset
                    )
                }
            }
        }
    }

    private fun View.fixLine(offset: Int) {
        // this.setPadding(0, 200, 0, 0)
        val layoutParams =
            this.layoutParams as FrameLayout.LayoutParams// FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,offset)
        layoutParams.setMargins(0, offset, 0, 0)
        this.layoutParams = layoutParams
    }

    var lockTop: Int? = null
    var lockBottom: Int? = null
    var currentScroll : Int = 0
    private fun updateTTSLine(line: TTSHelper.TTSLine?) {
        textAdapter.updateTTSLine(line)

        var minScroll: Int = Int.MAX_VALUE
        var maxScroll: Int = Int.MIN_VALUE
        // updates all the current views
        for (position in textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
            val viewHolder = binding.realText.findViewHolderForAdapterPosition(position)
            if (viewHolder !is TextAdapter.TextAdapterHolder) continue
            val (top, bottom) = viewHolder.updateTTSLine(line) ?: continue
            minScroll = min(top, minScroll)
            maxScroll = max(bottom, maxScroll)
        }
        if (maxScroll == Int.MIN_VALUE || minScroll == Int.MAX_VALUE) {
            lockTop = null
            lockBottom = null
            return
        }
        lockTop = currentScroll + minScroll
        //lockBottom = binding.realText.scrollY + maxScroll - getBottomY()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.realText.post {
            scrollToDesired()
            updateTTSLine(viewModel.ttsLine.value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        super.onCreate(savedInstanceState)
        readActivity = this
        binding = ReadMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registerBattery()

        viewModel.init(intent, this)

        textAdapter = TextAdapter(viewModel).apply {
            setHasStableIds(true)
        }
        textLayoutManager = LinearLayoutManager(binding.realText.context)

        binding.ttsActionPausePlay.setOnClickListener {
            viewModel.pausePlayTTS()
        }

        binding.ttsActionStop.setOnClickListener {
            viewModel.stopTTS()
        }

        binding.readActionTts.setOnClickListener {
            viewModel.startTTS()
        }

        binding.ttsActionForward.setOnClickListener {
            viewModel.forwardsTTS()
        }

        binding.ttsActionBack.setOnClickListener {
            viewModel.backwardsTTS()
        }

        observe(viewModel.orientation) { org ->
            requestedOrientation = org.flag
            binding.readActionRotate.setImageResource(org.iconRes)

            binding.readActionRotate.apply {
                setOnClickListener {
                    popupMenu(
                        items = OrientationType.values().map { it.prefValue to it.stringRes },
                        selectedItemId = org.prefValue
                    ) {
                        viewModel.setOrientation(OrientationType.fromSpinner(itemId))
                    }
                }
            }
        }

        observeNullable(viewModel.ttsLine) { line ->
            updateTTSLine(line)
        }

        observe(viewModel.title) { title ->
            binding.readToolbar.title = title
        }

        observe(viewModel.chapterTile) { title ->
            binding.readToolbar.subtitle = title
        }

        observe(viewModel.chaptersTitles) { titles ->
            binding.readActionChapters.setOnClickListener {
                val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
                //builderSingle.setIcon(R.drawable.ic_launcher)
                val currentChapter = viewModel.desiredIndex.index
                // cant be too safe here
                val validChapter = currentChapter >= 0 && currentChapter < titles.size
                if (validChapter) {
                    builderSingle.setTitle(titles[currentChapter]) //  "Select Chapter"
                } else {
                    builderSingle.setTitle(R.string.select_chapter)
                }

                val arrayAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)

                arrayAdapter.addAll(titles)

                builderSingle.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }

                builderSingle.setAdapter(arrayAdapter) { _, which ->
                    viewModel.seekToChapter(which)
                }

                val dialog = builderSingle.create()
                dialog.show()

                dialog.listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                if (validChapter) {
                    dialog.listView.setSelection(currentChapter)
                    dialog.listView.setItemChecked(currentChapter, true)
                }
            }
        }

        observe(viewModel.ttsStatus) { status ->
            val isTTSRunning = status != TTSHelper.TTSStatus.IsStopped

            binding.readerBottomView.isGone = isTTSRunning
            binding.readerBottomViewTts.isVisible = isTTSRunning
            binding.ttsActionPausePlay.setImageResource(
                when (status) {
                    TTSHelper.TTSStatus.IsPaused -> R.drawable.ic_baseline_play_arrow_24
                    TTSHelper.TTSStatus.IsRunning -> R.drawable.ic_baseline_pause_24
                    TTSHelper.TTSStatus.IsStopped -> R.drawable.ic_baseline_play_arrow_24
                }
            )
        }

        fixPaddingStatusbar(binding.readToolbarHolder)
        fixPaddingStatusbar(binding.realText)

        binding.apply {
            realText.setOnClickListener {
                viewModel.switchVisibility()
            }
            readToolbar.setOnClickListener {
                viewModel.switchVisibility()
            }
            readerLinContainer.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        observe(viewModel.bottomVisibility) { visibility ->
            if (visibility)
                showSystemUI()
            else
                hideSystemUI()
        }

        observe(viewModel.loadingStatus) { loading ->
            when (loading) {
                is Resource.Success -> {
                    binding.readLoading.isVisible = false
                    binding.readFail.isVisible = false

                    binding.readNormalLayout.isVisible = true
                    binding.readNormalLayout.alpha = 0.01f

                    ObjectAnimator.ofFloat(binding.readNormalLayout, "alpha", 1f).apply {
                        duration = 300
                        start()
                    }
                }

                is Resource.Loading -> {
                    binding.readNormalLayout.isVisible = false
                    binding.readFail.isVisible = false
                    binding.readLoading.isVisible = true
                    binding.loadingText.apply {
                        isGone = loading.url.isNullOrBlank()
                        text = loading.url ?: ""
                    }
                }

                is Resource.Failure -> {
                    binding.readLoading.isVisible = false
                    binding.readFail.isVisible = true
                    binding.failText.text = loading.errorString
                    binding.readNormalLayout.isVisible = false
                }
            }
        }


        binding.realText.apply {
            layoutManager = textLayoutManager
            adapter = textAdapter
            itemAnimator = null

            addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    var rdy = dy

                    onScroll()
                    lockTop?.let { lock ->
                        if(currentScroll+rdy > lock) {
                            rdy = lock-currentScroll
                        }
                    }

                    /*lockBottom?.let { lock ->
                        if(currentScroll+rdy < lock) {
                            rdy = lock-currentScroll
                        }
                    }*/

                    currentScroll += dy
                    val delta = rdy-dy
                    if(delta != 0) scrollBy(0,delta)
                    super.onScrolled(recyclerView, dx, dx)

                   // binding.tmpTtsEnd.fixLine((getBottomY()- remainingBottom) + 7.toPx)
                   // binding.tmpTtsStart.fixLine(remainingTop + 7.toPx)


                }
            })
        }

        observe(viewModel.chapter) { chapter ->
            cachedChapter = chapter.data
            textAdapter.submitList(chapter.data) {
                if (chapter.seekToDesired) {
                    scrollToDesired()
                }
                onScroll()
            }
        }
    }
}