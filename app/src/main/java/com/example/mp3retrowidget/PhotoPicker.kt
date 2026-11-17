package com.example.mp3retrowidget

import android.app.Activity
import android.content.Intent

/** Small helper shared by the settings screen and the skin picker. */
object PhotoPicker {

    const val REQUEST_PICK = 701
    const val REQUEST_CROP = 702

    fun pick(activity: Activity) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        activity.startActivityForResult(
            Intent.createChooser(intent, activity.getString(R.string.photo_pick)),
            REQUEST_PICK
        )
    }

    /**
     * Call from onActivityResult. Returns true when the result belonged to the
     * photo flow (so the caller knows it has to refresh).
     */
    fun onActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        when (requestCode) {
            REQUEST_PICK -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri = data?.data
                    if (uri != null) {
                        activity.startActivityForResult(
                            Intent(activity, CropActivity::class.java)
                                .putExtra(CropActivity.EXTRA_URI, uri.toString()),
                            REQUEST_CROP
                        )
                    }
                }
                return true
            }

            REQUEST_CROP -> return true
        }
        return false
    }
}
