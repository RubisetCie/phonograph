package com.kabouzeid.gramophone.interfaces;

import androidx.annotation.NonNull;

import com.kabouzeid.gramophone.util.materialcab.MaterialCab;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public interface CabHolder {

    @NonNull
    MaterialCab openCab(final int menuRes, final MaterialCab.Callback callback);
}
