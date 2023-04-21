package com.example.elaa

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.example.elaa.models.Post
import com.example.elaa.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

private const val TAG = "CreateActivity"
private const val PICK_PHOTO_CODE = 1234
class CreateActivity : AppCompatActivity() {
    private var signedInUser: User? = null
    private var photoUri: Uri?=null
    private lateinit var firestoreDb: FirebaseFirestore
    private lateinit var storageReference: StorageReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)
        storageReference = FirebaseStorage.getInstance().reference
        firestoreDb = FirebaseFirestore.getInstance()
        firestoreDb.collection("users")
            .document(FirebaseAuth.getInstance().currentUser?.uid as String)
            .get()
            .addOnSuccessListener { userSnapshot ->
                signedInUser = userSnapshot.toObject(User::class.java)
                Log.i(TAG, "signed in user: $signedInUser")
            }
            .addOnFailureListener { exception ->
                Log.i(TAG, "Failure fetching signed in user",exception)
            }

        findViewById<Button>(R.id.btnPickImage).setOnClickListener {
            Log.i(TAG, "Open up image picker on device")
            val imagePickerIntent = Intent(Intent.ACTION_GET_CONTENT)
            imagePickerIntent.type = "image/*" // we want to open any app which can handle this intent and provide us image
            if(imagePickerIntent.resolveActivity(packageManager) != null){
                startActivityForResult(imagePickerIntent, PICK_PHOTO_CODE)//We are using sratActivity For Result because we want to get the result back what ever happens on that application.
            }
        }

        findViewById<Button>(R.id.btnSubmit).setOnClickListener {
            handleSubmitButtonClick()
        }
    }

    private fun handleSubmitButtonClick() {
        if(photoUri == null){
            Toast.makeText(this, "No photo selected", Toast.LENGTH_SHORT).show()
            return
        }
        if (findViewById<EditText>(R.id.etDescription).text.isBlank()){
            Toast.makeText(this, "Description Cannot be empty!!", Toast.LENGTH_SHORT).show()
            return
        }
        if(signedInUser == null){
            Toast.makeText(this, "No signed in user", Toast.LENGTH_SHORT).show()
            return
        }

        findViewById<Button>(R.id.btnSubmit).isEnabled = false
        val photoUploadUri = photoUri as Uri // Encasted as a non-node uri
        val photoReference = storageReference.child("images/${System.currentTimeMillis()}-photo.jpg")
        //upload photo to firebase storage
        photoReference.putFile(photoUploadUri)
            .continueWithTask{photoUploadTask ->
                Log.i(TAG,"uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                //Retrieve image url of the uploaded image
                photoReference.downloadUrl
            }.continueWithTask { downloadUrlTask ->
                //create a post object with the image url and add that posts collection
                val post = Post(
                    findViewById<EditText>(R.id.etDescription).text.toString(),
                    downloadUrlTask.result.toString(),
                    System.currentTimeMillis(),
                    signedInUser)
                firestoreDb.collection("posts").add(post)
            }.addOnCompleteListener { postCreationTask ->
                findViewById<Button>(R.id.btnSubmit).isEnabled = true
                if(!postCreationTask.isSuccessful){
                    Log.e(TAG, "Exception during firebase operation", postCreationTask.exception)
                    Toast.makeText(this, "Failed to Save post", Toast.LENGTH_SHORT).show()
                }
                findViewById<EditText>(R.id.etDescription).text.clear()
                findViewById<ImageView>(R.id.imageView).setImageResource(0)
                Toast.makeText(this, "Success!!", Toast.LENGTH_SHORT).show()
                val profileIntent = Intent(this, PostsActivity::class.java)
                profileIntent.putExtra(EXTRA_USERNAME, signedInUser?.username)
                startActivity(profileIntent)
                finish()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICK_PHOTO_CODE){
            if(resultCode == Activity.RESULT_OK){
                photoUri = data?.data
                Log.i(TAG, "photoUri $photoUri")
                findViewById<ImageView>(R.id.imageView).setImageURI(photoUri)
            }else{
                Toast.makeText(this, "Image pick action cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}