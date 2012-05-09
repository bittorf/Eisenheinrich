package net.krautchan.backend;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Assert;

import net.krautchan.data.KCBoard;
import net.krautchan.data.KCThread;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	private final static String	DBASE_NAME 		= "Schlaubernd";
	private final static int 	VERSION_NUM 	= 2;
	private static final String BOARD_TABLE		= "board";
	private static final String THREAD_TABLE	= "thread";

	public DatabaseHelper(Context context) {
		super(context, DBASE_NAME, null, VERSION_NUM); 
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		try {
			createBoardTable (db);
			createThreadTable (db);
		} catch (SQLException ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 1) {
			try {
				db.beginTransaction();
				db.execSQL("DROP TABLE IF EXISTS "+BOARD_TABLE);
				createBoardTable (db);
				db.setTransactionSuccessful();
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				db.endTransaction();
			}
		}
	}
	 
	@Override
	public void close() {
		SQLiteDatabase db = getReadableDatabase();
		db.close();
	}

	public Collection <KCBoard> getBoards () {
		ConcurrentLinkedQueue<KCBoard> boards = new ConcurrentLinkedQueue<KCBoard>();
		Cursor c = retrieveAllBoards();
		c.moveToFirst();
		while (!c.isAfterLast()) {
			KCBoard board = new KCBoard();
			board.dbId = c.getLong (c.getColumnIndex("_id")); 
			board.uri = c.getString(c.getColumnIndex("url"));
			board.shortName = c.getString(c.getColumnIndex("shortname"));
			board.name = c.getString(c.getColumnIndex("name"));
			board.show = c.getInt(c.getColumnIndex("show")) != 0;
			boards.add(board);
			c.moveToNext();
		}
		c.close();
		return boards;
	}
	
	public void deleteThread(Long dbId) {
		SQLiteDatabase db = getReadableDatabase();
		String query = "delete from "
			+THREAD_TABLE+"  where id = ?";
		db.rawQuery(query,  new String[]{dbId.toString()});
	}
	
	public void bookmarkThread (KCThread thread) {
		if (null == thread) {
			return;
		}
		if (null == thread.board_id) {
			throw new IllegalArgumentException ("Board ID must not be null");
		}
		if (null == thread.kcNummer) {
			throw new IllegalArgumentException ("KC Number must not be null");
		}
		if (null == thread.uri) {
			throw new IllegalArgumentException ("Thread URI must not be null");
		}
		if (null == thread.digest) {
			throw new IllegalArgumentException ("Thread Digest must not be null");
		}
		if (null == thread.firstPostDate) {
			throw new IllegalArgumentException ("First post time must not be null");
		}
		if (getThread (thread.dbId) != null) {
			//already bookmarked, we are done
			return;
		}
		SQLiteDatabase db = getReadableDatabase();
		try{
			ContentValues valHolder = new ContentValues();
			valHolder.put("id", thread.dbId);
			valHolder.put("fk_board", thread.board_id);
			valHolder.put("kc_number", thread.kcNummer);
			valHolder.put("url", thread.uri);
			valHolder.put("digest", thread.digest);
			valHolder.put("time_inserted", new Date().getTime());
			valHolder.put("is_bookmarked", 1);
			db.insert(THREAD_TABLE, null, valHolder);
		} catch (SQLException e) {
			e.printStackTrace();
		} 
	}
	
	public KCThread getThread (Long id) {
		Assert.assertNotNull(id);
		KCThread thread = null;
		Cursor c = retrieveThread(id);
		c.moveToFirst();
		while (!c.isAfterLast()) {
			thread = new KCThread();
			thread.dbId = c.getLong (c.getColumnIndex("_id")); 
			thread.kcNummer = c.getLong (c.getColumnIndex("kc_number")); 
			thread.uri = c.getString(c.getColumnIndex("t_url"));
			thread.board_id = c.getLong (c.getColumnIndex("b_id")); 
			thread.digest = c.getString(c.getColumnIndex("digest"));
			c.moveToNext();
		}
		return thread;
	}
	
	public Collection <KCThread> getAllThreads () {
		return getThreads (false);
	}
	
	public Collection <KCThread> getBookmarks () {
		return getThreads (true);
	}
	
	private Collection <KCThread> getThreads (boolean bookmarksOnly) {
		ConcurrentLinkedQueue<KCThread> threads = new ConcurrentLinkedQueue<KCThread>();
		Cursor c = retrieveAllThreads(bookmarksOnly);
		c.moveToFirst();
		while (!c.isAfterLast()) {
			KCThread thread = new KCThread();
			thread.dbId = c.getLong (c.getColumnIndex("_id")); 
			thread.kcNummer = c.getLong (c.getColumnIndex("kc_number")); 
			thread.uri = c.getString(c.getColumnIndex("t_url"));
			thread.board_id = c.getLong (c.getColumnIndex("b_id")); 
			thread.digest = c.getString(c.getColumnIndex("digest"));
			threads.add(thread);
			c.moveToNext();
		}
		c.close();
		return threads;
	}
	
	public void persistBoards (Map<String, KCBoard> boards) {
		Set<String> keys = boards.keySet();
		List<KCBoard> boardL = new ArrayList<KCBoard>(keys.size());
		for (String key: keys) {
			boardL.add(boards.get(key));
		}
		persistBoards(boardL);
	}

	public void persistBoards (Collection<KCBoard> boards) {
		SQLiteDatabase db = getReadableDatabase();
		try{
			db.beginTransaction();
			db.execSQL("drop table "+BOARD_TABLE);
			createBoardTable (db);
			int loop = 1;
			for (KCBoard board :boards) {
				ContentValues valHolder = new ContentValues();
				valHolder.put("id", board.dbId);
				valHolder.put("shortname", board.shortName);
				valHolder.put("name", board.name);
				valHolder.put("url", board.uri);
				valHolder.put("sort_order", loop++);
				valHolder.put("show", board.show ? 1 : 0);
				db.insert(BOARD_TABLE, null, valHolder);
			}
			db.setTransactionSuccessful();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			db.endTransaction();
			db.execSQL("VACUUM");
		}
	}

	private Cursor retrieveAllBoards() {
		SQLiteDatabase db = getReadableDatabase();
		return db.query(BOARD_TABLE, new String[] { "id as _id",
				"shortname", "name", "url", "show" }, null, null, null, null, "sort_order");
	}
	
	private Cursor retrieveAllThreads(boolean bookmarksOnly) {
		SQLiteDatabase db = getReadableDatabase();
		String query = "select t.id _id, b.id b_id, t.kc_number, t.url t_url, t.digest from "
			+THREAD_TABLE+" t join "+BOARD_TABLE+" b "
			+" on t.fk_board = b.id ";
		if (bookmarksOnly) {
			query += " where t.is_bookmarked = 1";
		}
		return db.rawQuery(query, null);
	}
	
	private Cursor retrieveThread(Long id) {
		SQLiteDatabase db = getReadableDatabase();
		String query = "select t.id _id, b.id b_id, t.kc_number, t.url t_url from "
			+THREAD_TABLE+" t join "+BOARD_TABLE+" b "
			+" on t.fk_board = b.id  where t.id = ?";
		return db.rawQuery(query,  new String[]{id.toString()});
	}
	
	/*private Cursor retrieveAllThreads(boolean bookmarksOnly) {
		SQLiteDatabase db = getReadableDatabase();
		String query = "select id _id, kc_number, url, fk_board from "
			+THREAD_TABLE;
		if (bookmarksOnly) {
			query += " where is_bookmarked = 1";
		}
		return db.rawQuery(query, null);
	}*/
	

	private void createBoardTable (SQLiteDatabase db) throws SQLException {
		db.execSQL("create table "+BOARD_TABLE+" (id integer primary key, shortname text, name text, url text, sort_order integer, show integer)");		
	}
	
	private void createThreadTable (SQLiteDatabase db) throws SQLException {
		db.execSQL("create table "+THREAD_TABLE+" (id integer primary key,"
				+" fk_board integer not null, kc_number integer not null, "
				+" url text not null, digest text not null, time_inserted integer not null, is_bookmarked integer, "
				+" FOREIGN KEY(fk_board) REFERENCES "+BOARD_TABLE+"(id))");		
	}
	

}