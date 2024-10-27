package com.example.yemektariflerikitabi.view

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.room.Room
import com.example.yemektariflerikitabi.databinding.FragmentTarifBinding
import com.example.yemektariflerikitabi.model.Tarif
import com.example.yemektariflerikitabi.roomdb.TarifDAO
import com.example.yemektariflerikitabi.roomdb.TarifDatabase
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.ByteArrayOutputStream


class TarifFragment : Fragment() {

    private var _binding: FragmentTarifBinding? = null
    private val binding get() = _binding!!
    private lateinit var permissionLauncher : ActivityResultLauncher<String>
    private lateinit var activityResultLauncher : ActivityResultLauncher<Intent>
    private var secilenGorsel: Uri? = null
    private var secilenBitmap: Bitmap? = null //alınan veriyi görsele çevirmye yarar bitmap
    private val mDisposable = CompositeDisposable()
    private lateinit var db: TarifDatabase
    private lateinit var tarifDao: TarifDAO
    private var secilenTarif: Tarif? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerLauncher()

        db = Room.databaseBuilder(requireContext(), TarifDatabase::class.java, "Tarifler").build()
        tarifDao = db.tarifDao()

    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        //return inflater.inflate(R.layout.fragment_tarif, container, false)
        _binding = FragmentTarifBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageView.setOnClickListener { gorselSec(it) }
        binding.kaydetButton.setOnClickListener { kaydet(it) }
        binding.silButton.setOnClickListener { sil(it) }

        arguments?.let {
            val bilgi = TarifFragmentArgs.fromBundle(it).bilgi


            if (bilgi == "yeni") {
                //yeni bilgi eklenecek
                secilenTarif = null
                binding.silButton.isEnabled = false
                binding.kaydetButton.isEnabled = true
                binding.isimText.setText("")
                binding.malzemeText.setText("")
            } else {
                //eski eklenmiş tarif gösteriliyor
                binding.silButton.isEnabled = true
                binding.kaydetButton.isEnabled = false
                val id = TarifFragmentArgs.fromBundle(it).id

                mDisposable.add(
                    tarifDao.findById(id)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::handleResponse)
                )
            }
        }
    }

    private fun handleResponse(tarif: Tarif) {
        val bitmap = BitmapFactory.decodeByteArray(tarif.gorsel, 0, tarif.gorsel.size)
        binding.imageView.setImageBitmap(bitmap)
        binding.isimText.setText(tarif.isim)
        binding.malzemeText.setText(tarif.malzeme)
        secilenTarif = tarif
    }


    fun kaydet(view: View) {
        val isim = binding.isimText.text.toString()
        val malzeme = binding.malzemeText.text.toString()

        if (secilenBitmap != null) {
            val kucukBitmap = kucukBitmapOlustur(secilenBitmap!!, 300)
            val outputStream = ByteArrayOutputStream()
            kucukBitmap.compress(Bitmap.CompressFormat.PNG, 50, outputStream)
            val byteDizisi = outputStream.toByteArray()

            val tarif = Tarif(isim, malzeme, byteDizisi)
            tarifDao.insert(tarif)

            //RxJava

            mDisposable.add(
                tarifDao.insert(tarif)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleResponseForInsert)
            )


        }

    }

    private fun handleResponseForInsert() {
        //bir önceki fragmenta dön
        val action = TarifFragmentDirections.actionTarifFragmentToListeFragment()
        Navigation.findNavController(requireView()).navigate(action)

    }


    fun sil(view: View) {

        if (secilenTarif != null) {

            mDisposable.add(
                tarifDao.delete(tarif = secilenTarif!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::handleResponseForInsert)
            )
        }


    }


    fun gorselSec(view: View) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {


            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                //izin verilmmeiş izin istemmeiz gerekiyor
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        READ_MEDIA_IMAGES
                    )
                ) {
                    //snackbar göstermemiz lazım . kullanıcıdan neden izin istediğimizi söylememiz bir kez daha söylememiz lazım
                    Snackbar.make(
                        view,
                        "Galeriye Ulaşıp Görsel Seçmemiz Lazım!!",
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction("İzin Ver", View.OnClickListener {
                        //izin isteyeceğiz
                        permissionLauncher.launch(READ_MEDIA_IMAGES)
                    }).show()
                } else {
                    //izin isteyeceğiz
                    permissionLauncher.launch(READ_MEDIA_IMAGES)
                }

            } else {
                //izin verilmiş galeriye girebilirim
                val intentToGallery =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }


        } else {


            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                //izin verilmmeiş izin istemmeiz gerekiyor
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        requireActivity(),
                        READ_EXTERNAL_STORAGE
                    )
                ) {
                    //snackbar göstermemiz lazım . kullanıcıdan neden izin istediğimizi söylememiz bir kez daha söylememiz lazım
                    Snackbar.make(
                        view,
                        "Galeriye Ulaşıp Görsel Seçmemiz Lazım!!",
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction("İzin Ver", View.OnClickListener {
                        //izin isteyeceğiz
                        permissionLauncher.launch(READ_EXTERNAL_STORAGE)
                    }).show()
                } else {
                    //izin isteyeceğiz
                    permissionLauncher.launch(READ_EXTERNAL_STORAGE)
                }

            } else {
                //izin verilmiş galeriye girebilirim
                val intentToGallery =
                    Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                activityResultLauncher.launch(intentToGallery)
            }


        }

    }


    private fun registerLauncher() {

        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK) {
                    val intentFromResult = result.data
                    if (intentFromResult != null) {
                        secilenGorsel = intentFromResult.data
                        try {
                            if (Build.VERSION.SDK_INT >= 28) {
                                val source = ImageDecoder.createSource(
                                    requireActivity().contentResolver,
                                    secilenGorsel!!
                                )
                                secilenBitmap = ImageDecoder.decodeBitmap(source)
                                binding.imageView.setImageBitmap(secilenBitmap)
                            } else {
                                secilenBitmap = MediaStore.Images.Media.getBitmap(
                                    requireActivity().contentResolver,
                                    secilenGorsel
                                )
                                binding.imageView.setImageBitmap(secilenBitmap)
                            }
                        } catch (e: Exception) {
                            println(e.localizedMessage)
                        }
                    }
                }
            }




        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
                if (result) {
                    //izin verildi
                    //galeriye gidebiliriz
                    val intentToGallery =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    activityResultLauncher.launch(intentToGallery)
                } else {
                    //izin verilmedi
                    Toast.makeText(requireContext(), "İzin Verilmedi!!", Toast.LENGTH_LONG).show()
                }

            }
    }


    private fun kucukBitmapOlustur(kullanicininSectigiBitmap: Bitmap, maximumBoyut: Int): Bitmap {


        var width = kullanicininSectigiBitmap.width
        var height = kullanicininSectigiBitmap.height

        val bitmapOrani: Double = width.toDouble() / height.toDouble()

        if (bitmapOrani > 1) {
            //görsel yatay
            width = maximumBoyut
            val kisaltilmişYukselklik = width / bitmapOrani
            height = kisaltilmişYukselklik.toInt()


        } else {
            //gorsel dikey
            height = maximumBoyut
            val kisaltilmisGenislik = height * bitmapOrani
            width = kisaltilmisGenislik.toInt()
        }
        return Bitmap.createScaledBitmap(kullanicininSectigiBitmap, width, height, true)
    }


    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        mDisposable.clear()
    }
}