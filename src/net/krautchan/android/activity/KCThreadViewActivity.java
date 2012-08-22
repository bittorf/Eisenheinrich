package net.krautchan.android.activity;

/*
* Copyright (C) 2011 Johannes Jander (johannes@jandermail.de)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringEscapeUtils;

import junit.framework.Assert;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.*;
import android.webkit.WebStorage.QuotaUpdater;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import net.krautchan.R;
import net.krautchan.android.Eisenheinrich;
import net.krautchan.android.dialog.BannedDialog;
import net.krautchan.android.helpers.CustomExceptionHandler;
import net.krautchan.android.helpers.FileContentProvider;
import net.krautchan.android.helpers.ActivityHelpers;
import net.krautchan.android.helpers.FileHelpers;
import net.krautchan.android.helpers.HtmlCreator;
import net.krautchan.data.KCBoard;
import net.krautchan.data.KCPosting;
import net.krautchan.data.KCThread;
import net.krautchan.data.KODataListener;
import net.krautchan.parser.KCPageParser;

public class KCThreadViewActivity extends Activity {
	private static final String 	TAG = "KCThreadViewActivity";
	private PostingListener 		pListener = new PostingListener();
	private String					citation = "";		
	private static String 			template = null;
	private int						progressIncrement = 5;
	private WebView 				webView;
	private boolean					webViewBack = true;
	private Handler 				mHandler = new Handler();
	private String 					boardName = null;
	private Long 					boardId = null;
	private KCThread 				thread = null;
	private String					token;
	private boolean 				javascriptInterfaceBroken = false;
	private Handler 				progressHandler = null;
	private boolean					pageFinished = false;
	//private boolean					backlogRendered = false;
	private boolean					firstPostRendered = false;
	private boolean 				visitedPostsAreCollapsed = true;

	@Override
	public void onCreate(Bundle bndl) { 
		super.onCreate(bndl);
		Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler(
		        "eisenheinrich", "http://eisenheinrich.datensalat.net:8080/Eisenweb/upload/logfile/test", this));

		View v = this.getLayoutInflater().inflate(R.layout.kc_web_view, null);
		 
		webView = (WebView) v.findViewById(R.id.kcWebView);
		setContentView(v);
		findViewById(R.id.threadview_watcher_wrapper).setVisibility(View.VISIBLE);
		final ProgressBar progress = (ProgressBar)findViewById(R.id.threadview_watcher);
	    progress.setMax(100);
	    progress.setProgress(0);
	    progressHandler = new Handler() {
	        public void handleMessage(Message msg) {
	        	if (0 == msg.arg1) {
	        		findViewById(R.id.threadview_watcher_wrapper).setVisibility(View.GONE);
				    progress.setMax(100);
				    progress.setProgress(0);
				    //findViewById(R.id.webview_spinner).setVisibility(View.VISIBLE);
	        	} else if (1 == msg.arg1) {
		        	progress.incrementProgressBy(progressIncrement);
	        	} else if (2 == msg.arg1) {
				    //findViewById(R.id.webview_spinner).setVisibility(View.GONE);
	        	}
	        }
	    };
		webView.setBackgroundColor(Color.BLACK);
		WebSettings webSettings = webView.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(true);
		webSettings.setSupportZoom(false);
		webSettings.setAllowFileAccess(true);
		webSettings.setJavaScriptCanOpenWindowsAutomatically(false);
		webView.setWebViewClient(new KCWebViewClient());
		// TODO: http://krautchan.net/ajax/checkpost?board=b
		
		//TODO: review http://stackoverflow.com/questions/7424510/uncaught-typeerror-when-using-a-javascriptinterface
		/*
		 * Workaround for
		 * https://code.google.com/p/android/issues/detail?id=12987 WebView
		 * is borked on android 2.3 Workaround courtesy of
		 * http://quitenoteworthy.blogspot.com/2010/12/handling-android-23-webviews-broken.html
		 */
		
		try {
			if ((Build.VERSION.SDK_INT == 9) || (Build.VERSION.SDK_INT == 10)) { //Android 2.3.x
				javascriptInterfaceBroken = true;
				webView.setWebChromeClient(new KCWebChromeClient());
			} else {
				webView.addJavascriptInterface(new JavaScriptInterface (this), "Android");
				webView.setWebChromeClient(new KCWebChromeClient());
			}
		} catch (Exception e) {
			javascriptInterfaceBroken = true;
		}

		if (null == template) {
			template = prepareTemplate (getPageTemplate ());
		} 

		if (null != bndl) {
			webView.restoreState(bndl);  
			//ActivityHelpers.switchToThread(thread, this);
			//throw new UnsupportedOperationException ();
		} else {
			Bundle b = getIntent().getExtras();
			boardId = b.getLong("boardId"); 
			Long threadId = b.getLong("threadId");
			thread = Eisenheinrich.GLOBALS.getThreadCache().get(threadId);
			if ((null != thread) && (null != pListener)) {
				Eisenheinrich.getInstance().addPostListener(pListener);
			}
			Eisenheinrich.GLOBALS.setVisitedPostsCollapsible(b.getBoolean("visitedPostsCollapsible"));
			Assert.assertNotNull(boardId);
			
			token = b.getString("token");
			progressIncrement = b.getInt("progressIncrement");
			thread.uri = token;
			KCBoard board = Eisenheinrich.GLOBALS.getBoardCache().get(boardId);
			boardName = board.shortName;
			String title = "/"+boardName+"/"+thread.kcNummer;
			if (board.banned) {
				title = title + " ("+this.getString(R.string.banned)+")";
			}
			this.setTitle(title);
			
			renderHtml(template);
		}
		
		if (javascriptInterfaceBroken) {
			Eisenheinrich.GLOBALS.setVisitedPostsCollapsible(false);
		}
		if (Eisenheinrich.GLOBALS.areVisitedPostsCollapible()) {
			Button toggleCollapsedButton = (Button)findViewById(R.id.show_collapsed);
		    toggleCollapsedButton.setOnClickListener(new View.OnClickListener() {
			    public void onClick(View v) {
			    	v.setVisibility(View.GONE);
			    	visitedPostsAreCollapsed = !visitedPostsAreCollapsed;
			    	webView.loadUrl("javascript:showCollapsed ("+visitedPostsAreCollapsed+")");
			    }
		    });
		} 
		showHideCollapsedButton ();
		Log.i("THREADVIEW", "onCreate done");
	}
	
	private void showHideCollapsedButton () {
		runOnUiThread(new Runnable() {
	        public void run() {
				Button toggleCollapsedButton = (Button)findViewById(R.id.show_collapsed);
				if (thread.previousLastKcNum != null) {
					toggleCollapsedButton.setVisibility(View.VISIBLE);
				} else {
					toggleCollapsedButton.setVisibility(View.GONE);
				}
	        }
		});
	}
	
	private String prepareTemplate (String locTemplate) {
		Log.i("THREADVIEW", "prepareTemplate");
		locTemplate = locTemplate.replace("@@JSBRIDGESANE@@", Boolean.toString(!javascriptInterfaceBroken));
		if ((null != thread) && (null != thread.previousLastKcNum)) {
    		locTemplate = locTemplate.replace("@@CURPOST@@", thread.previousLastKcNum.toString());
    	} else {
    		locTemplate = locTemplate.replace("@@CURPOST@@", "null");
    	}
		String gingerbreadFix = "";
    	if (javascriptInterfaceBroken) {
        	gingerbreadFix = "function handler() { " +
				"this.openKcLink = function(url){alert(\"open:kclink:\"+url)}; " +
        		"this.openExternalLink = function(url){alert(\"open:extlink:\"+url)}; " +
        		"this.openYouTubeVideo = function(url){alert(\"open:ytlink:\"+videoId)}; " +
        		"this.openImage = function(url){alert(\"open:image:\"+fileName)}; " +
        		"this.citePosting = function(postid){alert(\"cite:\"+postid)}; " +
        		"this.debugString = function(str){alert(\"debugstr:\"+str)};" +
        		"}; " +
        		"var Android = new handler();";
    	}
    	locTemplate = locTemplate.replace("@@GINGERBREADFIX@@", gingerbreadFix);
    	return locTemplate;
	}
	
	public void onBackPressed () {
		/*if (!webViewBack) {
			Eisenheinrich.getInstance().removePostListener(pListener);
			super.onBackPressed();
			citation = "";
			//webView.clearHistory();
		}
		else if (webView.canGoBack()) {
			webView.goBack();
			webViewBack = false;
			//renderHtml(html); 
			//webView.clearHistory();
		} else {*/
			Eisenheinrich.getInstance().removePostListener(pListener);
			super.onBackPressed();
			citation = "";
		//}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.i("THREADVIEW", "onSaveInstanceState");
		webView.saveState(outState);
		//outState.putString("html", template);
		outState.putLong("threadId", thread.dbId);
		outState.putLong("threadKcNum", thread.kcNummer);
		outState.putLong("boardId", thread.board_id);
		outState.putString("token", thread.uri);
		Log.i("THREADVIEW", "onSaveInstanceState done");
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle inState) {
		Log.i("THREADVIEW", "onRestoreInstanceState");
		//String html = inState.getString("html");
		webView.restoreState(inState);
		thread = Eisenheinrich.GLOBALS.getThreadCache().get(inState.getLong("threadId"));
		//super.onRestoreInstanceState(inState);
		Log.i("THREADVIEW", "onRestoreInstanceState done");
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onStop() {
	    super.onStop();
	}

	@Override
	protected void onRestart() {
	    super.onRestart();
	} 

	private void renderHtml(final String html) {
		runOnUiThread(new Runnable() {
	        public void run() {
	        	webView.loadDataWithBaseURL("http://krautchan.net/", html, "text/html", "utf-8", null);
	        }
		});
		//this.html = html;
	}
	
	private void renderPosting (KCPosting posting, boolean read, boolean even, final boolean first) {
		Log.i("THREADVIEW", "Render Posting: "+posting.kcNummer);
		String classStr = ""; 
		if (!first) {
			if (read) {
				classStr = "read";
			} else {
				classStr = "unread";
			}
		}
		if (even) {
			classStr += " even";
		} else {
			classStr += " odd"; 
		}
		String content = HtmlCreator.htmlForPosting(posting, Eisenheinrich.GLOBALS.shouldShowImages());
		content = content.replaceAll("'", '\\'+"\\'").replaceAll("[\n\r]", " ").replaceAll("\"", "\\\\\"");
		final String cStr = classStr;
		final String cContent = content; 
		runOnUiThread(new Runnable() {
	        public void run() {
	        	if (first) {
	        		webView.loadUrl("javascript:appendPost('"+cContent+"', '"+cStr+"', 'first')");
	        	} else {
	        		webView.loadUrl("javascript:appendPost('"+cContent+"', '"+cStr+"', '')");
	        	}
	        }
		});
	}
	
	/*private void renderPostingBacklog () {
		Log.i("THREADVIEW", "renderPostingBacklog");
		boolean even = false;
		for (KCPosting posting: thread.getSortedPostings()) {
			renderPosting (posting, even);
			even = !even;
		}
		Log.i("THREADVIEW", "renderPostingBacklog done");
	}*/

	private final class PostingListener implements KODataListener<KCPosting> {
		private boolean even = false; 
		
		@Override
		public void notifyAdded(KCPosting item, Object token) {
			if (KCThreadViewActivity.this.token.equals(token)) {
				even = !even;
				((ProgressBar)findViewById(R.id.threadview_watcher)).incrementProgressBy(progressIncrement);
				Log.i("THREADVIEW", "notifyAdded 1: "+item.kcNummer);
				if (pageFinished) {
					if (item.kcNummer.longValue() != thread.kcNummer.longValue()) {
						boolean read = false;
						if (thread.previousLastKcNum != null) {
							if (item.kcNummer <= thread.previousLastKcNum) {
								read = true;
							}
						}
						renderPosting (item, read, even, false);
					}
					if (null == thread.previousLastKcNum)  {
						thread.previousLastKcNum = item.kcNummer;
					} else if (thread.previousLastKcNum < item.kcNummer) {
						thread.previousLastKcNum = item.kcNummer;
					}
				} 
			} 
		}  

		@Override
		public void notifyDone(Object token) {
			if (KCThreadViewActivity.this.token.equals(token)) {
				Log.i("THREADVIEW", "notifyDone");
				//backlogRendered = false;
				if (thread.getLastPosting() != null) {
					thread.previousLastKcNum = thread.getLastPosting().kcNummer;
				} 
				Eisenheinrich.getInstance().dbHelper.persistThread(thread);
				//qpageFinished = false;
				Message msg = progressHandler.obtainMessage();
	        	msg.arg1 = 0;
	        	//webView.loadUrl("javascript:postingsDone()");
	        	progressHandler.sendMessage(msg);
	        	//FIXME remove next line and implement thread caching.
	        	thread.recalc();
	        	stopSpinner ();
			}
		}

		@Override
		public void notifyError(Exception ex, Object token) {
			if (KCThreadViewActivity.this.token.equals(token)) {
				KCThreadViewActivity.this.finish();
			}
		}
	}

	private final class KCWebViewClient extends WebViewClient {
		 @Override
		    public boolean shouldOverrideUrlLoading(WebView  view, String  url){
		        return true;
		    }
		    
		 	// Override URL Loading
		    @Override
		    public void onLoadResource(WebView  view, String  url){
		        if( url.equals("http://cnn.com") ){
		            // do whatever you want
		        }
		        super.onLoadResource(view, url);
		    }

		@Override
		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);
			pageFinished = true;
			stopSpinner ();
			showHideCollapsedButton ();
			if ((null != thread) && (thread.getSortedPostings().iterator().hasNext())) {
				KCPosting posting = thread.getSortedPostings().iterator().next();
				renderPosting (posting, false, false, true);
				firstPostRendered = true;
			}
		}
	} 
	
	private void stopSpinner () {
		Message msg = progressHandler.obtainMessage();
    	msg.arg1 = 2;
    	progressHandler.sendMessage(msg);
	}

	final class JavaScriptInterface {
		Context context;

	    JavaScriptInterface(Context c) {
	        context = c;
	    }

	    public void citePosting(final String postid) {
	    	mHandler.post(new Runnable() {
				public void run() {
					KCThreadViewActivity.this.citePosting(postid);
				}
			});
	    }
	    
	    public void openExternalLink(final String url) {
			mHandler.post(new Runnable() {
				public void run() {
					KCThreadViewActivity.this.openExternalLink(url);
				}
			});
		}
	    
	    public void openKcLink(final String url) {
	    	mHandler.post(new Runnable() {
				public void run() {
					KCThreadViewActivity.this.openKcLink(url) ;
				}
			});
	    }
		
	    public void openImage (final String fileName) {
			mHandler.post(new Runnable() {
				public void run() {
					KCThreadViewActivity.this.openImage (fileName);
				}
			});
		}
		
	    public void openYouTubeVideo(final String videoID) {
			mHandler.post(new Runnable() {
				public void run() {
					KCThreadViewActivity.this.openYouTubeVideo(videoID);
				}
			});
		}
	    
	    public void debugString(final String str) {
 	    	System.out.println (str);
	    }
	}
	
	public void citePosting(String postid) {
    	long postDbId = -1;
    	try {
    		postDbId = Long.parseLong(postid);
    		KCPosting post = KCThreadViewActivity.this.thread.getPosting(postDbId);
    		if (null == post) {
    			return;
    		}
    		citation += ">>"+post.kcNummer+" :\n"+post.getKcStyledContent()+"\n";
    		
    	} catch (Exception ex) {
    		Log.e(TAG, "citePosting failed: "+ex.getMessage());
	        return;
    	}
    }
	
	private void openExternalLink(String url) {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			url = "http://" + url;
		}
		Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
		try {
			startActivity(browser);
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(getApplicationContext(), this.getText(R.string.could_not_open_browser), Toast.LENGTH_LONG).show();
		}
	}
	
	private void openKcLink(String url) {
		String[] parts = url.split("/");
		List<KCBoard> boards = Eisenheinrich.GLOBALS.getBoardCache().getAll();
		Iterator<KCBoard> iter = boards.iterator();
		boolean found = false;
		KCBoard board = null;
		while (iter.hasNext() && (!found)) {
			board = iter.next();
			if (board.shortName.equals(parts[1])) {
				found = true;
			}
		}
		if (null != board) {
			prepareForRerender(board, Long.parseLong(parts[2]));
			//ActivityHelpers.switchToThread(Long.parseLong(parts[2]), parts[1], board.dbId,  KCThreadViewActivity.this);
		}
	}
	
	private void openImage (String fileName) {
		try {
			Uri uri = Uri.parse(FileContentProvider.URI_PREFIX+"/"+fileName);
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(getApplicationContext(), this.getText(R.string.could_not_open_image_viewer), Toast.LENGTH_LONG).show();
		}
	}
	
	/*
	 * Lifted from:
	 * http://it-ride.blogspot.com/2010/04/android-youtube-intent.html
	 */
	private void openYouTubeVideo(String videoID) {
		String id = videoID.replace("youtube.com/watch?v=", "");
		Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:"+ id));
		List<ResolveInfo> list = getPackageManager().queryIntentActivities(i,
				PackageManager.MATCH_DEFAULT_ONLY);
		if (list.size() > 0) {
			startActivity(i);
		} else {
			Toast.makeText(getApplicationContext(), getText(R.string.could_not_open_youtube_viewer), Toast.LENGTH_LONG).show();
		}
	}

	/**
	 * Provides a hook for calling "alert" from javascript. We use it to work
	 * around the broken JS-Bridge
	 * Args are in the form command:type:id
	 * Commands Defined: 
	 *  - open
	 *  - cite
	 * Types defined: 
	 *  - kclink
	 *  - external link
	 *  - youtube
	 *  - image
	 */
	final class KCWebChromeClient extends WebChromeClient {
		@Override
		public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
			Log.d(TAG, message);
			result.confirm();
			String args[] = message.split(":");
			if (args[0].equals("open")) {
				if (args[1].equals("ytlink")) {
					openYouTubeVideo(args[2]);
				} else if (args[1].equals("image")) {
					openImage (args[2]);
				} else if (args[1].equals("extlink")) {
					openExternalLink (args[2]);
				}  else if (args[1].equals("kclink")) {
					openKcLink (args[2]);
				}
			} else if (args[0].equals("cite")){
				citePosting (args[1]); 
			}
			return true;
		}

		@Override
		public boolean onConsoleMessage(ConsoleMessage cm) {
			//Log.e(TAG, ">>>"+cm.message());
			//FileHelpers.writeToSDFile("_log_.txt", cm.message());
			return super.onConsoleMessage(cm); 
		}

		@Override
		public void onReachedMaxAppCacheSize(long spaceNeeded,
				long totalUsedQuota, QuotaUpdater quotaUpdater) {
			Log.e(TAG, "MaxAppCacheSize reached");
			super.onReachedMaxAppCacheSize(spaceNeeded, totalUsedQuota, quotaUpdater);
		}
		
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.options_menu_webview, menu);
		return true;
	}
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (0 == requestCode) {
			reload();
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
	
	private String getPageTemplate() {
		final String templateName = "kc_thread_view_template.html";
		String template = null;
		String css = Eisenheinrich.STYLES.getStyles();
		InputStream templateStream = null;
		try {
			templateStream = this.getAssets().open(templateName);
			BufferedReader r = new BufferedReader(new InputStreamReader(templateStream));
			StringBuilder builder = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null) {
				builder.append(line);
			}
			r.close();
			templateStream.close();
			r = null;
			template = builder.toString();
			if (null != css) {
				template = template.replace("@@CSS@@", css);
			}
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		return template;
	}

	private void prepareForRerender(KCBoard board, long threadKcNum) {
		thread.board_id = board.dbId;
		thread.kcNummer = threadKcNum;
		boardName = board.shortName;
		thread.clearPostings();
		findViewById(R.id.threadview_watcher_wrapper).setVisibility(View.VISIBLE);
		String title = "/"+boardName+"/"+thread.kcNummer;
		if (board.banned) {
			title = title + " ("+this.getString(R.string.banned)+")";
		}
		KCThreadViewActivity.this.setTitle(title);
		token = "http://krautchan.net/" + board.shortName + "/thread-" + threadKcNum + ".html";
		Thread t = new Thread(new KCPageParser("http://krautchan.net/" + board.shortName + "/thread-" + threadKcNum + ".html", board.dbId)
			.setBasePath("http://krautchan.net/")
			.setThreadHandler(
					Eisenheinrich.getInstance().getThreadListener())
			.setPostingHandler(
					Eisenheinrich.getInstance().getPostListener()));
		t.start();
	}
	
	private void reload() {
		if (Eisenheinrich.GLOBALS.areVisitedPostsCollapible()) {
			findViewById(R.id.show_collapsed).setVisibility(View.VISIBLE);
			visitedPostsAreCollapsed = true;
	    	webView.loadUrl("javascript:markAllPostingsRead ()");
		} 
		webView.loadUrl("javascript:showCollapsed (true)");
		findViewById(R.id.threadview_watcher_wrapper).setVisibility(View.VISIBLE);
		/*if (null != thread.getLastPosting()) {
			thread.previousLastKcNum = thread.getLastPosting().kcNummer;
		}*/
		
		Thread t = new Thread(new KCPageParser(thread)
			.setBasePath("http://krautchan.net/")
			.setThreadHandler(
				Eisenheinrich.getInstance().getThreadListener())
			.setPostingHandler(
				Eisenheinrich.getInstance().getPostListener()));
		t.start();
		
		//ActivityHelpers.switchToThread(thread, this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.bookmark: 
			Eisenheinrich.getInstance().dbHelper.bookmarkThread(thread);
			return true;
		case R.id.reload: 
			reload();
			return true;
		case R.id.prefs:
			return true;
		case R.id.reply: 
			KCBoard board = Eisenheinrich.GLOBALS.getBoardCache().get(boardId);
 			String cc = Eisenheinrich.GLOBALS.getKomturCode();
			if ((board.banned) && (null == cc)) {
				new BannedDialog (this).show();
				Toast.makeText(KCThreadViewActivity.this, R.string.banned_message, Toast.LENGTH_LONG).show();
			} else {
				ActivityHelpers.createThreadMask (thread, boardId, citation, this);
			}
			return true;
		case R.id.home: 
			Intent intent = new Intent(KCThreadViewActivity.this, EisenheinrichActivity.class);
			startActivity(intent);
			this.finish();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
}