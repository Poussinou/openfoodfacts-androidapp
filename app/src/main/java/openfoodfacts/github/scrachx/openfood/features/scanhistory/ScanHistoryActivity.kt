package openfoodfacts.github.scrachx.openfood.features.scanhistory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import openfoodfacts.github.scrachx.openfood.AppFlavor
import openfoodfacts.github.scrachx.openfood.AppFlavor.Companion.isFlavors
import openfoodfacts.github.scrachx.openfood.BuildConfig
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.databinding.ActivityHistoryScanBinding
import openfoodfacts.github.scrachx.openfood.features.product.view.ProductViewActivityStarter
import openfoodfacts.github.scrachx.openfood.features.productlist.CreateCSVContract
import openfoodfacts.github.scrachx.openfood.features.shared.BaseActivity
import openfoodfacts.github.scrachx.openfood.listeners.CommonBottomListenerInstaller.installBottomNavigation
import openfoodfacts.github.scrachx.openfood.listeners.CommonBottomListenerInstaller.selectNavigationItem
import openfoodfacts.github.scrachx.openfood.models.Barcode
import openfoodfacts.github.scrachx.openfood.models.HistoryProduct
import openfoodfacts.github.scrachx.openfood.utils.Intent
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import openfoodfacts.github.scrachx.openfood.utils.SortType.BARCODE
import openfoodfacts.github.scrachx.openfood.utils.SortType.BRAND
import openfoodfacts.github.scrachx.openfood.utils.SortType.GRADE
import openfoodfacts.github.scrachx.openfood.utils.SortType.TIME
import openfoodfacts.github.scrachx.openfood.utils.SortType.TITLE
import openfoodfacts.github.scrachx.openfood.utils.SwipeController
import openfoodfacts.github.scrachx.openfood.utils.isHardwareCameraInstalled
import openfoodfacts.github.scrachx.openfood.utils.shouldLoadImages
import openfoodfacts.github.scrachx.openfood.utils.writeHistoryToFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ScanHistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryScanBinding
    private val viewModel: ScanHistoryViewModel by viewModels()

    @Inject
    lateinit var productViewActivityStarter: ProductViewActivityStarter

    @Inject
    lateinit var picasso: Picasso

    @Inject
    lateinit var localeManager: LocaleManager

    /**
     * boolean to determine if menu buttons should be visible or not
     */
    private var menuButtonsEnabled = false

    private val adapter by lazy { ScanHistoryAdapter(shouldLoadImages(), picasso, ::onProductClick) }

    private fun onProductClick(product: HistoryProduct) {
        val barcode = Barcode(product.barcode)
        productViewActivityStarter.openProduct(barcode, this)
    }

    private val storagePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                exportAsCSV()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_title)
                    .setMessage(R.string.permission_denied)
                    .setNegativeButton(R.string.txtNo) { dialog, _ -> dialog.dismiss() }
                    .setPositiveButton(R.string.txtYes) { dialog, _ ->
                        startActivity(Intent().apply {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.fromParts("package", this@ScanHistoryActivity.packageName, null)
                        })
                        dialog.dismiss()
                    }
                    .show()
            }
        }

    private val cameraPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startScanActivity()
            }
        }

    private val fileWriterLauncher = registerForActivityResult(CreateCSVContract()) { uri ->
        uri?.let {
            writeHistoryToFile(this, adapter.products, it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.getBoolean(R.bool.portrait_only)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        binding = ActivityHistoryScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.scan_history_drawer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.listHistoryScan.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.listHistoryScan.adapter = adapter
        val swipeController = SwipeController(this) { position ->
            adapter.products.getOrNull(position)?.let {
                viewModel.removeProductFromHistory(it)
            }
        }
        ItemTouchHelper(swipeController).attachToRecyclerView(binding.listHistoryScan)

        binding.scanFirst.setOnClickListener { startScan() }
        binding.srRefreshHistoryScanList.setOnRefreshListener { refreshViewModel() }
        binding.navigationBottom.bottomNavigation.installBottomNavigation(this)

        viewModel.productsState
            .flowWithLifecycle(lifecycle)
            .onEach { state ->
                when (state) {
                    is ScanHistoryViewModel.FetchProductsState.Data -> showData(state)
                    ScanHistoryViewModel.FetchProductsState.Error -> showError()
                    ScanHistoryViewModel.FetchProductsState.Loading -> showLoading()
                }
            }.launchIn(lifecycleScope)

        refreshViewModel()
    }

    private fun showData(state: ScanHistoryViewModel.FetchProductsState.Data) {
        binding.srRefreshHistoryScanList.isRefreshing = false
        binding.historyProgressbar.isVisible = false

        adapter.products = state.items

        if (state.items.isEmpty()) {
            setMenuEnabled(false)
            binding.scanFirstProductContainer.isVisible = true
        } else {
            binding.scanFirstProductContainer.isVisible = false
            setMenuEnabled(true)
        }

        adapter.notifyItemRangeChanged(0, state.items.count())
    }

    private fun showError() {
        setMenuEnabled(false)
        binding.srRefreshHistoryScanList.isRefreshing = false
        binding.historyProgressbar.isVisible = false
        binding.scanFirstProductContainer.isVisible = true
    }

    private fun showLoading() {
        setMenuEnabled(false)
        if (!binding.srRefreshHistoryScanList.isRefreshing) {
            binding.historyProgressbar.isVisible = true
        }
    }

    private fun refreshViewModel() = viewModel.refreshItems()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.clear()

        if (menuButtonsEnabled) {
            menuInflater.inflate(R.menu.menu_history, menu)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> {
            NavUtils.navigateUpFromSameTask(this)
            true
        }

        R.id.action_remove_all_history -> {
            showDeleteConfirmationDialog()
            true
        }

        R.id.action_export_all_history -> {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.action_about)
                        .setMessage(R.string.permision_write_external_storage)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                        .show()

                } else {
                    storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            } else {
                exportAsCSV()
            }
            true
        }

        R.id.sort_history -> {
            showListSortingDialog()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }


    override fun onResume() {
        super.onResume()
        binding.navigationBottom.bottomNavigation.selectNavigationItem(R.id.history_bottom_nav)
    }

    private fun setMenuEnabled(enabled: Boolean) {
        menuButtonsEnabled = enabled
        invalidateOptionsMenu()
    }


    private fun exportAsCSV() {
        Toast.makeText(this, R.string.txt_exporting_history, Toast.LENGTH_LONG).show()

        val flavor = BuildConfig.FLAVOR.uppercase(Locale.ROOT)
        val date = SimpleDateFormat("yyyy-MM-dd", localeManager.getLocale()).format(Date())
        val fileName = "$flavor-history_$date.csv"

        fileWriterLauncher.launch(fileName)
    }

    private fun startScan() {
        if (!isHardwareCameraInstalled(this)) return
        // TODO: 21/06/2021 add dialog to explain why we can't
        val perm = Manifest.permission.CAMERA
        when {
            ContextCompat.checkSelfPermission(baseContext, perm) == PackageManager.PERMISSION_GRANTED -> {
                startScanActivity()
            }

            ActivityCompat.shouldShowRequestPermissionRationale(this, perm) -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.action_about)
                    .setMessage(R.string.permission_camera)
                    .setPositiveButton(android.R.string.ok) { d, _ ->
                        d.dismiss()
                        cameraPermLauncher.launch(perm)
                    }
                    .show()
            }

            else -> {
                cameraPermLauncher.launch(perm)
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_clear_history_dialog)
            .setMessage(R.string.text_clear_history_dialog)
            .setPositiveButton(android.R.string.ok) { d, _ ->
                viewModel.clearHistory()
                d.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .show()
    }

    private val sortTypes by lazy {
        if (isFlavors(AppFlavor.OFF)) arrayOf(
            TITLE,
            BRAND,
            GRADE,
            BARCODE,
            TIME,
        ) else arrayOf(
            TITLE,
            BRAND,
            TIME,
            BARCODE,
        )
    }

    private fun showListSortingDialog() {
        val selectedItemIdx = sortTypes.indexOf(viewModel.sortType.value)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(
                sortTypes.map { getString(it.stringRes) }.toTypedArray(),
                if (selectedItemIdx < 0) 0 else selectedItemIdx
            ) { dialog, idx ->
                val newType = sortTypes[idx]
                viewModel.updateSortType(newType)
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        fun start(context: Context) = context.startActivity(Intent<ScanHistoryActivity>(context))
    }
}
