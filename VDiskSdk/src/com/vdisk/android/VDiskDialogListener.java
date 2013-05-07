
package com.vdisk.android;

import android.os.Bundle;

import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;

public interface VDiskDialogListener {

    public void onComplete(Bundle values);

    public void onError(VDiskDialogError error);

    public void onVDiskException(VDiskException exception);

    public void onCancel();

}
