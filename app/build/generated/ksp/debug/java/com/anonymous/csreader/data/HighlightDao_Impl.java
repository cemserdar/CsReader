package com.anonymous.csreader.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class HighlightDao_Impl implements HighlightDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HighlightEntity> __insertionAdapterOfHighlightEntity;

  private final EntityDeletionOrUpdateAdapter<HighlightEntity> __updateAdapterOfHighlightEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteHighlightById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteHighlightsByBookId;

  public HighlightDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHighlightEntity = new EntityInsertionAdapter<HighlightEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `highlights` (`id`,`bookId`,`cfiRange`,`page`,`text`,`note`,`color`,`date`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HighlightEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getBookId());
        if (entity.getCfiRange() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCfiRange());
        }
        if (entity.getPage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPage());
        }
        statement.bindString(5, entity.getText());
        if (entity.getNote() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getNote());
        }
        statement.bindString(7, entity.getColor());
        statement.bindLong(8, entity.getDate());
      }
    };
    this.__updateAdapterOfHighlightEntity = new EntityDeletionOrUpdateAdapter<HighlightEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `highlights` SET `id` = ?,`bookId` = ?,`cfiRange` = ?,`page` = ?,`text` = ?,`note` = ?,`color` = ?,`date` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HighlightEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getBookId());
        if (entity.getCfiRange() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getCfiRange());
        }
        if (entity.getPage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindLong(4, entity.getPage());
        }
        statement.bindString(5, entity.getText());
        if (entity.getNote() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getNote());
        }
        statement.bindString(7, entity.getColor());
        statement.bindLong(8, entity.getDate());
        statement.bindString(9, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteHighlightById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM highlights WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteHighlightsByBookId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM highlights WHERE bookId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertHighlight(final HighlightEntity highlight,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfHighlightEntity.insert(highlight);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateHighlight(final HighlightEntity highlight,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfHighlightEntity.handle(highlight);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteHighlightById(final String id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteHighlightById.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteHighlightById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteHighlightsByBookId(final String bookId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteHighlightsByBookId.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, bookId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteHighlightsByBookId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HighlightEntity>> getHighlightsForBook(final String bookId) {
    final String _sql = "SELECT * FROM highlights WHERE bookId = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bookId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"highlights"}, new Callable<List<HighlightEntity>>() {
      @Override
      @NonNull
      public List<HighlightEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfCfiRange = CursorUtil.getColumnIndexOrThrow(_cursor, "cfiRange");
          final int _cursorIndexOfPage = CursorUtil.getColumnIndexOrThrow(_cursor, "page");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final List<HighlightEntity> _result = new ArrayList<HighlightEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HighlightEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpBookId;
            _tmpBookId = _cursor.getString(_cursorIndexOfBookId);
            final String _tmpCfiRange;
            if (_cursor.isNull(_cursorIndexOfCfiRange)) {
              _tmpCfiRange = null;
            } else {
              _tmpCfiRange = _cursor.getString(_cursorIndexOfCfiRange);
            }
            final Integer _tmpPage;
            if (_cursor.isNull(_cursorIndexOfPage)) {
              _tmpPage = null;
            } else {
              _tmpPage = _cursor.getInt(_cursorIndexOfPage);
            }
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            _item = new HighlightEntity(_tmpId,_tmpBookId,_tmpCfiRange,_tmpPage,_tmpText,_tmpNote,_tmpColor,_tmpDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getHighlightsForBookList(final String bookId,
      final Continuation<? super List<HighlightEntity>> $completion) {
    final String _sql = "SELECT * FROM highlights WHERE bookId = ? ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, bookId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HighlightEntity>>() {
      @Override
      @NonNull
      public List<HighlightEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfCfiRange = CursorUtil.getColumnIndexOrThrow(_cursor, "cfiRange");
          final int _cursorIndexOfPage = CursorUtil.getColumnIndexOrThrow(_cursor, "page");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final List<HighlightEntity> _result = new ArrayList<HighlightEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HighlightEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpBookId;
            _tmpBookId = _cursor.getString(_cursorIndexOfBookId);
            final String _tmpCfiRange;
            if (_cursor.isNull(_cursorIndexOfCfiRange)) {
              _tmpCfiRange = null;
            } else {
              _tmpCfiRange = _cursor.getString(_cursorIndexOfCfiRange);
            }
            final Integer _tmpPage;
            if (_cursor.isNull(_cursorIndexOfPage)) {
              _tmpPage = null;
            } else {
              _tmpPage = _cursor.getInt(_cursorIndexOfPage);
            }
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            _item = new HighlightEntity(_tmpId,_tmpBookId,_tmpCfiRange,_tmpPage,_tmpText,_tmpNote,_tmpColor,_tmpDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<HighlightEntity>> getAllHighlights() {
    final String _sql = "SELECT * FROM highlights ORDER BY date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"highlights"}, new Callable<List<HighlightEntity>>() {
      @Override
      @NonNull
      public List<HighlightEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfCfiRange = CursorUtil.getColumnIndexOrThrow(_cursor, "cfiRange");
          final int _cursorIndexOfPage = CursorUtil.getColumnIndexOrThrow(_cursor, "page");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfNote = CursorUtil.getColumnIndexOrThrow(_cursor, "note");
          final int _cursorIndexOfColor = CursorUtil.getColumnIndexOrThrow(_cursor, "color");
          final int _cursorIndexOfDate = CursorUtil.getColumnIndexOrThrow(_cursor, "date");
          final List<HighlightEntity> _result = new ArrayList<HighlightEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HighlightEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpBookId;
            _tmpBookId = _cursor.getString(_cursorIndexOfBookId);
            final String _tmpCfiRange;
            if (_cursor.isNull(_cursorIndexOfCfiRange)) {
              _tmpCfiRange = null;
            } else {
              _tmpCfiRange = _cursor.getString(_cursorIndexOfCfiRange);
            }
            final Integer _tmpPage;
            if (_cursor.isNull(_cursorIndexOfPage)) {
              _tmpPage = null;
            } else {
              _tmpPage = _cursor.getInt(_cursorIndexOfPage);
            }
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final String _tmpNote;
            if (_cursor.isNull(_cursorIndexOfNote)) {
              _tmpNote = null;
            } else {
              _tmpNote = _cursor.getString(_cursorIndexOfNote);
            }
            final String _tmpColor;
            _tmpColor = _cursor.getString(_cursorIndexOfColor);
            final long _tmpDate;
            _tmpDate = _cursor.getLong(_cursorIndexOfDate);
            _item = new HighlightEntity(_tmpId,_tmpBookId,_tmpCfiRange,_tmpPage,_tmpText,_tmpNote,_tmpColor,_tmpDate);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
