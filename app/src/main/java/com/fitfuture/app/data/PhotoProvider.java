package com.fitfuture.app.data;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Narrow, grant-only provider for a camera app to write one private photo. */
public final class PhotoProvider extends ContentProvider {
  @Override
  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) throws FileNotFoundException {
    try {
      File directory = new File(getContext().getFilesDir(), "photos").getCanonicalFile();
      String name = uri.getLastPathSegment();
      if (name == null || !name.matches("meal-[A-Za-z0-9-]+\\.jpg"))
        throw new FileNotFoundException();
      File f = new File(directory, name).getCanonicalFile();
      if (!directory.equals(f.getParentFile())) throw new FileNotFoundException();
      return f;
    } catch (IOException e) {
      throw new FileNotFoundException();
    }
  }

  @Override
  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode));
  }

  @Override
  public String getType(Uri uri) {
    return "image/jpeg";
  }

  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    try {
      File f = file(uri);
      MatrixCursor c =
          new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
      c.addRow(new Object[] {f.getName(), f.length()});
      return c;
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int update(Uri uri, ContentValues values, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int delete(Uri uri, String selection, String[] args) {
    throw new UnsupportedOperationException();
  }
}
