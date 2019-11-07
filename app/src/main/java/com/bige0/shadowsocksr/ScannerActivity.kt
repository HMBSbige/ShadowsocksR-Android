package com.bige0.shadowsocksr

import android.*
import android.app.TaskStackBuilder
import android.content.pm.*
import android.os.*
import android.text.*
import androidx.appcompat.app.*
import androidx.appcompat.widget.*
import androidx.core.app.*
import androidx.core.content.*
import com.bige0.shadowsocksr.utils.*
import com.google.zxing.*
import me.dm7.barcodescanner.zxing.*

class ScannerActivity : AppCompatActivity(), ZXingScannerView.ResultHandler
{
	private lateinit var scannerView: ZXingScannerView

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray)
	{
		if (requestCode == MY_PERMISSIONS_REQUEST_CAMERA)
		{
			// If request is cancelled, the result arrays are empty.
			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			{
				scannerView.setResultHandler(this)
				scannerView.startCamera()
			}
			else
			{
				ToastUtils.showShort(R.string.add_profile_scanner_permission_required)
				finish()
			}
		}
	}

	private fun navigateUp()
	{
		val intent = parentActivityIntent
		if (shouldUpRecreateTask(intent) || isTaskRoot)
		{
			TaskStackBuilder.create(this)
				.addNextIntentWithParentStack(intent)
				.startActivities()
		}
		else
		{
			finish()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?)
	{
		super.onCreate(savedInstanceState)
		setContentView(R.layout.layout_scanner)
		val toolbar = findViewById<Toolbar>(R.id.toolbar)
		toolbar.title = title
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material)
		toolbar.setNavigationOnClickListener { navigateUp() }

		scannerView = findViewById(R.id.scanner)

		if (Build.VERSION.SDK_INT >= 25)
		{
			getSystemService<ShortcutManager>()!!.reportShortcutUsed("scan")
		}
	}

	override fun onResume()
	{
		super.onResume()
		val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
		if (permissionCheck == PackageManager.PERMISSION_GRANTED)
		{
			// Register ourselves as a handler for scan results.
			scannerView.setResultHandler(this)
			scannerView.setAutoFocus(true)
			// Start camera on resume
			scannerView.startCamera()
		}
		else
		{
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), MY_PERMISSIONS_REQUEST_CAMERA)
		}
	}

	override fun onPause()
	{
		super.onPause()
		// Stop camera on pause
		scannerView.stopCamera()
	}

	override fun handleResult(rawResult: Result)
	{
		val uri = rawResult.text
		if (!TextUtils.isEmpty(uri))
		{
			val all = Parser.findAllSs(uri)
			if (all.isNotEmpty())
			{
				for (p in all)
				{
					ShadowsocksApplication.app.profileManager.createProfile(p)
				}
			}

			val allSSR = Parser.findAllSsr(uri)
			if (allSSR.isNotEmpty())
			{
				for (p in allSSR)
				{
					ShadowsocksApplication.app.profileManager.createProfile(p)
				}
			}
		}
		navigateUp()
	}

	companion object
	{
		private const val MY_PERMISSIONS_REQUEST_CAMERA = 1
	}
}
