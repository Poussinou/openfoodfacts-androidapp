/*
 * Copyright 2016-2020 Open Food Facts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package openfoodfacts.github.scrachx.openfood.features.product.view.nutrition

import android.Manifest.permission
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.NO_ID
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_SHORT
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import openfoodfacts.github.scrachx.openfood.AppFlavor
import openfoodfacts.github.scrachx.openfood.AppFlavor.Companion.isFlavors
import openfoodfacts.github.scrachx.openfood.R
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabActivityHelper
import openfoodfacts.github.scrachx.openfood.customtabs.CustomTabsHelper
import openfoodfacts.github.scrachx.openfood.customtabs.WebViewFallback
import openfoodfacts.github.scrachx.openfood.databinding.FragmentNutritionProductBinding
import openfoodfacts.github.scrachx.openfood.features.FullScreenActivityOpener
import openfoodfacts.github.scrachx.openfood.features.adapters.NutrimentsGridAdapter
import openfoodfacts.github.scrachx.openfood.features.images.manage.ImagesManageActivity
import openfoodfacts.github.scrachx.openfood.features.login.LoginActivity
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity
import openfoodfacts.github.scrachx.openfood.features.product.edit.ProductEditActivity.Companion.KEY_STATE
import openfoodfacts.github.scrachx.openfood.features.product.view.CalculateDetailsActivity
import openfoodfacts.github.scrachx.openfood.features.product.view.ProductViewActivity
import openfoodfacts.github.scrachx.openfood.features.shared.BaseFragment
import openfoodfacts.github.scrachx.openfood.features.shared.adapters.NutrientLevelListAdapter
import openfoodfacts.github.scrachx.openfood.images.ProductImage
import openfoodfacts.github.scrachx.openfood.models.CARBO_MAP
import openfoodfacts.github.scrachx.openfood.models.FAT_MAP
import openfoodfacts.github.scrachx.openfood.models.MINERALS_MAP
import openfoodfacts.github.scrachx.openfood.models.MeasurementUnit.ENERGY_KCAL
import openfoodfacts.github.scrachx.openfood.models.MeasurementUnit.ENERGY_KJ
import openfoodfacts.github.scrachx.openfood.models.NutrientLevelItem
import openfoodfacts.github.scrachx.openfood.models.Nutriment
import openfoodfacts.github.scrachx.openfood.models.NutrimentListItem
import openfoodfacts.github.scrachx.openfood.models.PROT_MAP
import openfoodfacts.github.scrachx.openfood.models.Product
import openfoodfacts.github.scrachx.openfood.models.ProductImageField
import openfoodfacts.github.scrachx.openfood.models.ProductNutriments
import openfoodfacts.github.scrachx.openfood.models.ProductState
import openfoodfacts.github.scrachx.openfood.models.VITAMINS_MAP
import openfoodfacts.github.scrachx.openfood.models.bold
import openfoodfacts.github.scrachx.openfood.models.buildLevelItem
import openfoodfacts.github.scrachx.openfood.models.entities.SendProduct
import openfoodfacts.github.scrachx.openfood.network.ApiFields.StateTags
import openfoodfacts.github.scrachx.openfood.repositories.ProductRepository
import openfoodfacts.github.scrachx.openfood.utils.ClickableSpan
import openfoodfacts.github.scrachx.openfood.utils.LOCALE_FILE_SCHEME
import openfoodfacts.github.scrachx.openfood.utils.LocaleManager
import openfoodfacts.github.scrachx.openfood.utils.MY_PERMISSIONS_REQUEST_CAMERA
import openfoodfacts.github.scrachx.openfood.utils.Measurement
import openfoodfacts.github.scrachx.openfood.utils.PhotoReceiverHandler
import openfoodfacts.github.scrachx.openfood.utils.getNutriScoreResource
import openfoodfacts.github.scrachx.openfood.utils.getRoundNumber
import openfoodfacts.github.scrachx.openfood.utils.getServingInL
import openfoodfacts.github.scrachx.openfood.utils.getServingInOz
import openfoodfacts.github.scrachx.openfood.utils.hideKeyboard
import openfoodfacts.github.scrachx.openfood.utils.isPowerSaveMode
import openfoodfacts.github.scrachx.openfood.utils.isImageLoadingDisabled
import openfoodfacts.github.scrachx.openfood.utils.isPerServingInLiter
import openfoodfacts.github.scrachx.openfood.utils.isUserSet
import openfoodfacts.github.scrachx.openfood.utils.requireProductState
import pl.aprilapps.easyphotopicker.EasyImage
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class NutritionProductFragment : BaseFragment(), CustomTabActivityHelper.ConnectionCallback {
    private var _binding: FragmentNutritionProductBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NutritionProductViewModel by viewModels()

    @Inject
    lateinit var client: ProductRepository

    @Inject
    lateinit var picasso: Picasso

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var localeManager: LocaleManager

    @Inject
    lateinit var photoReceiverHandler: PhotoReceiverHandler

    private lateinit var product: Product

    /**
     * Boolean to determine if nutrition data should be shown
     */
    private var showNutritionData = true

    /**
     * Boolean to determine if image should be loaded or not
     */
    private val isLowBatteryMode by lazy {
        requireContext().isImageLoadingDisabled() && requireContext().isPowerSaveMode()
    }

    private var nutrientsImageUrl: String? = null
    private var mSendProduct: SendProduct? = null
    private var customTabActivityHelper: CustomTabActivityHelper? = null
    private var nutritionScoreUri: Uri? = null

    /**
     * The following booleans indicate whether the prompts are to be made visible
     */
    private var showNutritionPrompt = false
    private var showCategoryPrompt = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNutritionProductBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // use VERTICAL divider
        val dividerItemDecoration = DividerItemDecoration(
            binding.nutrimentsRecyclerView.context,
            DividerItemDecoration.VERTICAL
        )
        binding.nutrimentsRecyclerView.addItemDecoration(dividerItemDecoration)
        binding.getNutriscorePrompt.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_box_blue_18dp, 0, 0, 0)
        binding.newAdd.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_add_a_photo_blue_18dp, 0, 0, 0)

        binding.nutriscoreLink.setOnClickListener { openNutriScoreLink() }
        binding.imageViewNutrition.setOnClickListener { openFullScreen() }
        binding.calculateNutritionFacts.setOnClickListener { calculateNutritionFacts() }
        binding.getNutriscorePrompt.setOnClickListener { onNutriScoreButtonClick() }
        binding.newAdd.setOnClickListener { doChooseOrTakePhotos() }

        viewModel.productState.value = requireProductState()
        viewModel.productState.observe(viewLifecycleOwner) { refreshView(it) }
    }


    companion object {
        fun newInstance(productState: ProductState) = NutritionProductFragment().apply {
            arguments = Bundle().apply { putSerializable(KEY_STATE, productState) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    override fun refreshView(productState: ProductState) {
        super.refreshView(productState)

        val langCode = localeManager.getLanguage()
        product = productState.product!!

        checkPrompts()
        showPrompts()

        if (!showNutritionData) {
            binding.imageViewNutrition.visibility = GONE
            binding.addPhotoLabel.visibility = GONE
            binding.imageGradeLayout.visibility = GONE
            binding.calculateNutritionFacts.visibility = GONE
            binding.nutrimentsCardView.visibility = GONE
            binding.textNoNutritionData.visibility = VISIBLE
        }

        val nutriments = product.nutriments
        if (Nutriment.CARBON_FOOTPRINT !in nutriments) {
            binding.textCarbonFootprint.visibility = GONE
        }
        setupNutrientItems(nutriments)

        // Checks the flags and accordingly sets the text of the prompt
        showPrompts()

        binding.textNutriScoreInfo.isClickable = true
        binding.textNutriScoreInfo.movementMethod = LinkMovementMethod.getInstance()

        val clickableSpan = ClickableSpan {
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.putExtra(
                "android.intent.extra.REFERRER",
                "android-app://${requireActivity().packageName}".toUri()
            )
            CustomTabActivityHelper.openCustomTab(
                requireActivity(),
                customTabsIntent,
                getString(R.string.url_nutrient_values).toUri(),
                WebViewFallback()
            )
        }

        binding.textNutriScoreInfo.text = buildSpannedString {
            inSpans(clickableSpan) {
                append(getString(R.string.txtNutriScoreInfo))
            }
        }

        binding.nutriscoreLink.isVisible = product.nutritionGradeFr != null

        var servingSize: Measurement? = null
        val servingSizeString = product.servingSize
        if (servingSizeString.isNullOrEmpty()) {
            binding.textServingSize.visibility = GONE
            binding.servingSizeCardView.visibility = GONE
        } else {
            val pref = sharedPreferences.getString(getString(R.string.pref_volume_unit_key), "l")

            if (pref.equals("oz", true)) {
                servingSize = getServingInOz(servingSizeString)
            } else if (pref.equals("l", true) && servingSizeString.contains("oz", true)) {
                servingSize = getServingInL(servingSizeString)
            }

            servingSize?.let {
                binding.textServingSize.text = buildSpannedString {
                    bold { append(getString(R.string.txtServingSize)) }
                    append(" ")
                    append("${getRoundNumber(it.value)} ${it.unit}")
                }
            }
        }

        if (arguments != null) {
            mSendProduct = requireArguments().getSerializable("sendProduct") as SendProduct?
        }

        val nutrimentListItems = mutableListOf<NutrimentListItem>()
        val inVolume = product.isPerServingInLiter()
        binding.textNutrientTxt.setText(if (inVolume != true) R.string.txtNutrientLevel100g else R.string.txtNutrientLevel100ml)

        if (!product.servingSize.isNullOrBlank()) {
            binding.textPerPortion.text = "${getString(R.string.nutriment_serving_size)} ${product.servingSize}"
        } else {
            binding.textPerPortion.visibility = GONE
        }

        if (!product.getImageNutritionUrl(langCode).isNullOrBlank()) {
            binding.addPhotoLabel.visibility = GONE
            binding.newAdd.visibility = VISIBLE

            // Load Image if isLowBatteryMode is false
            if (!isLowBatteryMode) {
                picasso
                    .load(product.getImageNutritionUrl(langCode))
                    .into(binding.imageViewNutrition)
            } else {
                binding.imageViewNutrition.visibility = GONE
            }
            picasso.load(product.getImageNutritionUrl(langCode))
                .into(binding.imageViewNutrition)
            nutrientsImageUrl = product.getImageNutritionUrl(langCode)
        }

        // Useful when this fragment is used in offline saving
        if (mSendProduct != null && mSendProduct!!.imgUploadNutrition.isNotBlank()) {
            binding.addPhotoLabel.visibility = GONE
            nutrientsImageUrl = mSendProduct!!.imgUploadNutrition
            picasso.load(LOCALE_FILE_SCHEME + nutrientsImageUrl)
                .config(Bitmap.Config.RGB_565)
                .into(binding.imageViewNutrition)
        }

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        binding.nutrimentsRecyclerView.setHasFixedSize(true)

        // use a linear layout manager
        val mLayoutManager = LinearLayoutManager(requireActivity())
        binding.nutrimentsRecyclerView.layoutManager = mLayoutManager
        binding.nutrimentsRecyclerView.isNestedScrollingEnabled = false

        // Header hack
        nutrimentListItems += NutrimentListItem(inVolume == true)

        // Energy
        val energyKcal = nutriments[Nutriment.ENERGY_KCAL]
        if (energyKcal != null) {
            nutrimentListItems += NutrimentListItem(
                getString(R.string.nutrition_energy_kcal),
                nutriments.getEnergyKcalValue(false)?.value,
                nutriments.getEnergyKcalValue(true)?.value,
                ENERGY_KCAL,
                energyKcal.modifier
            )
        }
        val energyKj = nutriments[Nutriment.ENERGY_KJ]
        if (energyKj != null) {
            nutrimentListItems += NutrimentListItem(
                getString(R.string.nutrition_energy_kj),
                nutriments.getEnergyKjValue(false)?.value,
                nutriments.getEnergyKjValue(true)?.value,
                ENERGY_KJ,
                energyKj.modifier
            )
        }

        // Fat
        val fat2 = nutriments[Nutriment.FAT]
        if (fat2 != null) {
            nutrimentListItems += NutrimentListItem(getString(R.string.nutrition_fat), fat2).bold()
            nutrimentListItems.addAll(getNutrimentItems(nutriments, FAT_MAP))
        }

        // Carbohydrates
        val carbohydrates = nutriments[Nutriment.CARBOHYDRATES]
        if (carbohydrates != null) {
            nutrimentListItems += NutrimentListItem(getString(R.string.nutrition_carbohydrate), carbohydrates).bold()
            nutrimentListItems += getNutrimentItems(nutriments, CARBO_MAP)
        }

        // fiber
        nutrimentListItems += getNutrimentItems(nutriments, mapOf(Nutriment.FIBER to R.string.nutrition_fiber))

        // Proteins
        val proteins = nutriments[Nutriment.PROTEINS]
        if (proteins != null) {
            nutrimentListItems += NutrimentListItem(getString(R.string.nutrition_proteins), proteins).bold()
            nutrimentListItems += getNutrimentItems(nutriments, PROT_MAP)
        }

        // salt and alcohol
        val map = mapOf(
            Nutriment.SALT to R.string.nutrition_salt,
            Nutriment.SODIUM to R.string.nutrition_sodium,
            Nutriment.ALCOHOL to R.string.nutrition_alcohol
        )
        nutrimentListItems += getNutrimentItems(nutriments, map)

        // Vitamins
        if (nutriments.hasVitamins) {
            nutrimentListItems += NutrimentListItem(getString(R.string.nutrition_vitamins)).bold()
            nutrimentListItems += getNutrimentItems(nutriments, VITAMINS_MAP)
        }

        // Minerals
        if (nutriments.hasMinerals) {
            nutrimentListItems += NutrimentListItem(getString(R.string.nutrition_minerals)).bold()
            nutrimentListItems += getNutrimentItems(nutriments, MINERALS_MAP)
        }

        // Show nutrition table and nutrition per portion button if nutritional values are available
        if (nutrimentListItems.size > 1) {
            binding.calculateNutritionFacts.visibility = VISIBLE
            binding.nutrimentsRecyclerView.adapter = NutrimentsGridAdapter(nutrimentListItems)
        }

    }

    private fun setupNutrientItems(nutriments: ProductNutriments) {
        val levelItemList = mutableListOf<NutrientLevelItem>()
        val nutrientLevels = product.nutrientLevels

        val fat = nutrientLevels?.fat
        val saturatedFat = nutrientLevels?.saturatedFat
        val sugars = nutrientLevels?.sugars
        val salt = nutrientLevels?.salt

        if (fat == null && salt == null && saturatedFat == null && sugars == null) {
            levelItemList += NutrientLevelItem("", "", "", NO_ID)
            binding.nutrientLevelsCardView.visibility = GONE
            updateNutritionGradeImg(false)
        } else {
            // prefetch the uri
            customTabActivityHelper = CustomTabActivityHelper()
            customTabActivityHelper!!.connectionCallback = this
            nutritionScoreUri = getString(R.string.nutriscore_uri).toUri()
            customTabActivityHelper!!.mayLaunchUrl(nutritionScoreUri, null, null)

            levelItemList += listOfNotNull(
                nutriments.buildLevelItem(requireContext(), Nutriment.FAT, fat),
                nutriments.buildLevelItem(requireContext(), Nutriment.SATURATED_FAT, saturatedFat),
                nutriments.buildLevelItem(requireContext(), Nutriment.SUGARS, sugars),
                nutriments.buildLevelItem(requireContext(), Nutriment.SALT, salt)
            )
            updateNutritionGradeImg(true)
        }

        binding.listNutrientLevels.apply {
            adapter = NutrientLevelListAdapter(requireActivity(), levelItemList)
            layoutManager = LinearLayoutManager(requireActivity())
        }
    }

    private fun updateNutritionGradeImg(show: Boolean) {
        if (!show) {
            binding.imageGradeLayout.isGone = true
            return
        }

        binding.imageGradeLayout.isGone = false
        binding.imageGrade.setImageResource(product.getNutriScoreResource())

        binding.imageGrade.setOnClickListener {
            CustomTabActivityHelper.openCustomTab(
                requireActivity(),
                CustomTabsHelper.getCustomTabsIntent(requireContext(), customTabActivityHelper!!.session),
                nutritionScoreUri!!,
                WebViewFallback()
            )
        }
    }


    /**
     * Checks the product states_tags to determine which prompt to be shown
     */
    private fun checkPrompts() {
        // Category
        if (StateTags.CATEGORIES_TO_BE_COMPLETED.tag in product.statesTags) {
            showCategoryPrompt = true
        }

        // Nutrition
        if (product.isNoNutrition()) {
            showNutritionPrompt = false
            showNutritionData = false
        } else if (StateTags.NUTRITION_FACTS_TO_BE_COMPLETED.tag in product.statesTags) {
            showNutritionPrompt = true
        }
    }

    private fun showPrompts() {
        binding.getNutriscorePrompt.visibility = VISIBLE
        binding.getNutriscorePrompt.text = when {
            showNutritionPrompt && showCategoryPrompt -> getString(R.string.add_nutrient_category_prompt_text)
            showNutritionPrompt -> getString(R.string.add_nutrient_prompt_text)
            showCategoryPrompt -> getString(R.string.add_category_prompt_text)
            else -> {
                binding.getNutriscorePrompt.visibility = GONE
                return
            }
        }
    }

    private fun getNutrimentItems(
        productNutriments: ProductNutriments,
        nutrimentMap: Map<Nutriment, Int>,
    ): List<NutrimentListItem> {
        return nutrimentMap.mapNotNull { (nutriment, stringRes) ->
            val productNutriment = productNutriments[nutriment] ?: return@mapNotNull null
            NutrimentListItem(getString(stringRes), productNutriment)
        }
    }

    private fun openNutriScoreLink() {
        if (product.nutritionGradeFr == null) return
        val customTabsIntent =
            CustomTabsHelper.getCustomTabsIntent(requireActivity(), customTabActivityHelper!!.session)
        CustomTabActivityHelper.openCustomTab(requireActivity(),
            customTabsIntent,
            nutritionScoreUri!!,
            WebViewFallback())
    }

    private fun openFullScreen() {
        if (nutrientsImageUrl != null) {
            lifecycleScope.launch {
                FullScreenActivityOpener.openForUrl(
                    this@NutritionProductFragment,
                    client,
                    product,
                    ProductImageField.NUTRITION,
                    nutrientsImageUrl!!,
                    binding.imageViewNutrition,
                    localeManager.getLanguage()
                )
            }
        } else {
            // take a picture
            when {
                checkSelfPermission(requireActivity(), permission.CAMERA) != PackageManager.PERMISSION_GRANTED -> {
                    requestPermissions(requireActivity(), arrayOf(permission.CAMERA), MY_PERMISSIONS_REQUEST_CAMERA)
                }
                else -> {
                    EasyImage.openCamera(this, 0)
                }
            }
        }
    }

    private fun calculateNutritionFacts() {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.calculate_nutrition_facts)
            .setView(R.layout.dialog_calculate_calories)
            .setOnDismissListener {
                requireActivity().hideKeyboard()
            }
            .show()

        (dialog.findViewById(R.id.spinner_weight) as? Spinner)?.apply {
            onItemSelectedListener = object : OnItemSelectedListener {
                override fun onNothingSelected(adapterView: AdapterView<*>?) = Unit

                override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                    (dialog.findViewById(R.id.txt_calories_result) as? Button)?.setOnClickListener {
                        val weight = (dialog.findViewById(R.id.edit_text_weight) as? EditText)
                            ?.text
                            ?.toString()
                            ?.toFloatOrNull()
                        if (weight == null) {
                            Snackbar.make(
                                binding.root,
                                resources.getString(R.string.please_enter_weight),
                                LENGTH_SHORT
                            ).show()
                        } else {
                            CalculateDetailsActivity.start(
                                requireActivity(),
                                product,
                                selectedItem.toString(),
                                weight
                            )
                            dialog.dismiss()
                        }
                    }
                }
            }
        }
    }

    private fun loadNutritionPhoto(photoFile: File) {
        // Create a new instance of ProductImage so we can load to server
        val image = ProductImage(
            product.code,
            ProductImageField.NUTRITION,
            photoFile,
            localeManager.getLanguage()
        ).apply {
            filePath = photoFile.absolutePath
        }

        // Load to server
        lifecycleScope.launch { client.postImg(image) }

        // Load into view
        binding.addPhotoLabel.visibility = GONE
        nutrientsImageUrl = photoFile.absolutePath
        picasso
            .load(photoFile)
            .fit()
            .into(binding.imageViewNutrition)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        photoReceiverHandler.onActivityResult(this, requestCode, resultCode, data) { loadNutritionPhoto(it) }

        if (ImagesManageActivity.isImageModified(requestCode, resultCode)) {
            (activity as? ProductViewActivity)?.onRefresh()
        }
    }

    override fun doOnPhotosPermissionGranted() = doChooseOrTakePhotos()

    override fun onCustomTabsConnected() {
        binding.imageGrade.isClickable = true
    }

    override fun onCustomTabsDisconnected() {
        binding.imageGrade.isClickable = false
    }

    private val loginThenEditLauncher = registerForActivityResult(LoginActivity.Companion.LoginContract())
    { logged -> if (logged) startEditProduct() }

    private fun onNutriScoreButtonClick() {
        if (!isFlavors(AppFlavor.OFF, AppFlavor.OBF)) return

        if (requireActivity().isUserSet()) startEditProduct()
        else loginThenEditLauncher.launch(null)
    }

    private fun startEditProduct() {
        ProductEditActivity.start(
            requireContext(),
            product,
            showCategoryPrompt = showCategoryPrompt,
            showNutritionPrompt = showNutritionPrompt
        )
    }
}
