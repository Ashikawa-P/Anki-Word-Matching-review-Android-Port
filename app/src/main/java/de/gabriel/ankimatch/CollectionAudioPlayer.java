package de.gabriel.ankimatch;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

/** Plays collection.media files from a user-granted Storage Access Framework directory. */
final class CollectionAudioPlayer {
    interface ErrorListener {
        void onError(String filename);
    }

    private final ContentResolver resolver;
    private MediaPlayer current;

    CollectionAudioPlayer(ContentResolver resolver) {
        this.resolver = resolver;
    }

    synchronized void play(Uri mediaTree, String filename, ErrorListener listener) {
        stop();
        Uri direct = directChildUri(mediaTree, filename);
        if (start(direct, filename, listener)) {
            return;
        }
        Uri queried = findChildUri(mediaTree, filename);
        if (queried == null || !start(queried, filename, listener)) {
            listener.onError(filename);
        }
    }

    synchronized void stop() {
        if (current == null) {
            return;
        }
        MediaPlayer player = current;
        current = null;
        try {
            player.stop();
        } catch (Exception ignored) {
        }
        player.release();
    }

    private boolean start(Uri uri, String filename, ErrorListener listener) {
        if (uri == null) {
            return false;
        }
        MediaPlayer player = new MediaPlayer();
        try (ParcelFileDescriptor descriptor = resolver.openFileDescriptor(uri, "r")) {
            if (descriptor == null) {
                player.release();
                return false;
            }
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
            player.setDataSource(descriptor.getFileDescriptor());
            current = player;
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(done -> releaseIfCurrent(done));
            player.setOnErrorListener((failed, what, extra) -> {
                releaseIfCurrent(failed);
                listener.onError(filename);
                return true;
            });
            player.prepareAsync();
            return true;
        } catch (Exception error) {
            if (current == player) {
                current = null;
            }
            player.release();
            return false;
        }
    }

    private synchronized void releaseIfCurrent(MediaPlayer player) {
        if (current != player) {
            return;
        }
        current = null;
        player.release();
    }

    private static Uri directChildUri(Uri treeUri, String filename) {
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId + "/" + filename);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Uri findChildUri(Uri treeUri, String filename) {
        try {
            String rootId = DocumentsContract.getTreeDocumentId(treeUri);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId);
            String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            };
            try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
                if (cursor == null) {
                    return null;
                }
                int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                while (cursor.moveToNext()) {
                    if (idColumn >= 0 && nameColumn >= 0 && filename.equals(cursor.getString(nameColumn))) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
