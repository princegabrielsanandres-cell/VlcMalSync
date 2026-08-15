package com.example.vlcmasync
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.widget.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.security.*
import java.util.Base64

class MainActivity:Activity(){
 companion object{private const val CLIENT_ID="PUT_YOUR_MAL_CLIENT_ID_HERE";private const val REDIRECT="malvlcsync://oauth"}
 private val api=MalApi(OkHttpClient());private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private lateinit var status:TextView;private var verifier:String?=null;private var selected:MalAnime?=null
 override fun onCreate(b:Bundle?){super.onCreate(b);handle(intent)
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32)}
  val title=TextView(this).apply{text="VLC → MAL Sync";textSize=25f}
  status=TextView(this).apply{text="MAL not connected";textSize=16f}
  val login=Button(this).apply{text="Connect MyAnimeList";setOnClickListener{login()}}
  val q=EditText(this).apply{hint="Anime title"}
  val find=Button(this).apply{text="Find anime on MAL";setOnClickListener{find(q.text.toString())}}
  val mark=Button(this).apply{text="Mark selected episode watched";setOnClickListener{mark()}}
  box.addView(title);box.addView(status);box.addView(login);box.addView(q);box.addView(find);box.addView(mark);setContentView(box)
 }
 private fun login(){if(CLIENT_ID.startsWith("PUT_")){status.text="Add your MAL Client ID first.";return}
  val x=ByteArray(32).also{SecureRandom().nextBytes(it)};verifier=Base64.getUrlEncoder().withoutPadding().encodeToString(x)
  val ch=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier!!.toByteArray()))
  val u=Uri.Builder().scheme("https").authority("myanimelist.net").path("/v1/oauth2/authorize").appendQueryParameter("response_type","code").appendQueryParameter("client_id",CLIENT_ID).appendQueryParameter("redirect_uri",REDIRECT).appendQueryParameter("code_challenge",ch).appendQueryParameter("code_challenge_method","S256").build()
  startActivity(Intent(Intent.ACTION_VIEW,u))
 }
 private fun handle(i:Intent?){val d=i?.data?:return;if(d.scheme!="malvlcsync"||d.host!="oauth")return;val code=d.getQueryParameter("code")?:return;val v=verifier?:return
  scope.launch(Dispatchers.IO){try{val t=api.token(CLIENT_ID,code,v,REDIRECT);getPreferences(0).edit().putString("token",t).apply();ui("MAL connected ✓")}catch(e:Exception){ui("Login failed: ${e.message}")}}
 }
 private fun tok():String?=getPreferences(0).getString("token",null)?:run{status.text="Connect MAL first.";null}
 private fun find(q:String){val t=tok()?:return;if(q.isBlank()){status.text="Enter an anime title.";return};scope.launch(Dispatchers.IO){try{val r=api.search(t,q);withContext(Dispatchers.Main){if(r.isEmpty())status.text="No matches.";else AlertDialog.Builder(this@MainActivity).setTitle("Choose MAL match").setItems(r.map{"${it.id} — ${it.title}"}.toTypedArray()){_,w->selected=r[w];status.text="Selected: ${r[w].title}"}.show()}}catch(e:Exception){ui("Search failed: ${e.message}")}}}
 private fun mark(){val t=tok()?:return;val a=selected?:run{status.text="Select an anime first.";return};val e=EditText(this).apply{hint="Episode number";inputType=2};AlertDialog.Builder(this).setTitle("Mark watched").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Sync"){_,_->val n=e.text.toString().toIntOrNull();if(n==null||n<0){status.text="Invalid episode.";return@setPositiveButton};scope.launch(Dispatchers.IO){try{api.update(t,a.id,n);ui("✓ ${a.title}: episode $n synced")}catch(x:Exception){ui("Update failed: ${x.message}")}}}.show()}
 private fun ui(s:String)=runOnUiThread{status.text=s}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
