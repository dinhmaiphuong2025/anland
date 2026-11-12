package com.anland.consumer.hud;

import java.util.ArrayList;

/**
 * Undo/redo stack for the live HUD editor.
 *
 * <p>Every entry is a complete serialized layout snapshot instead of a single
 * edit operation. {@link HudLayout} already round-trips through JSON
 * ({@link HudLayout#toJSON()} / {@link HudLayout#fromJSON(org.json.JSONObject)}),
 * so a full snapshot is both cheap to produce and exact to restore: there is no
 * need to write an inverse operation for every mutation the editor performs
 * (drag, resize, regroup, preset load, inspector change), and no risk of the
 * inverse drifting out of sync with the model.
 *
 * <p>The cursor points at the snapshot that represents the current state. A new
 * record discards everything after the cursor (the redo tail) before appending,
 * which is the standard linear undo model.
 */
final class HudHistory {

    private final ArrayList<String> mSnapshots = new ArrayList<>();
    private final int mCapacity;
    /** Index of the snapshot matching the current layout state; -1 when empty. */
    private int mCursor = -1;

    HudHistory(int maxSteps) {
        mCapacity = Math.max(2, maxSteps);
    }

    /**
     * Appends a new state. Anything that was redoable is dropped first, and the
     * oldest entries are discarded once the capacity is exceeded so the stack
     * stays bounded during a long editing session.
     */
    void record(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        // A gesture that ends where it started must not cost an undo step that
        // does nothing when pressed.
        if (mCursor >= 0 && snapshot.equals(mSnapshots.get(mCursor))) {
            while (mSnapshots.size() > mCursor + 1) {
                mSnapshots.remove(mSnapshots.size() - 1);
            }
            return;
        }
        while (mSnapshots.size() > mCursor + 1) {
            mSnapshots.remove(mSnapshots.size() - 1);
        }
        mSnapshots.add(snapshot);
        while (mSnapshots.size() > mCapacity) {
            mSnapshots.remove(0);
        }
        mCursor = mSnapshots.size() - 1;
    }

    /** Drops the whole stack and starts over with the given state as the base. */
    void reset(String snapshot) {
        mSnapshots.clear();
        mCursor = -1;
        record(snapshot);
    }

    boolean canUndo() {
        return mCursor > 0;
    }

    boolean canRedo() {
        return mCursor >= 0 && mCursor < mSnapshots.size() - 1;
    }

    /** Steps the cursor back and returns that state, or null when there is none. */
    String undo() {
        if (!canUndo()) return null;
        mCursor--;
        return mSnapshots.get(mCursor);
    }

    /** Steps the cursor forward and returns that state, or null when there is none. */
    String redo() {
        if (!canRedo()) return null;
        mCursor++;
        return mSnapshots.get(mCursor);
    }

    boolean isEmpty() {
        return mSnapshots.isEmpty();
    }

    void clear() {
        mSnapshots.clear();
        mCursor = -1;
    }
}
