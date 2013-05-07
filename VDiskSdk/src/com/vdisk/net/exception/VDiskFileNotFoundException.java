package com.vdisk.net.exception;

import java.io.IOException;

public class VDiskFileNotFoundException extends VDiskException {

	private static final long serialVersionUID = 1L;

	public VDiskFileNotFoundException(IOException e) {
		super(e);
	}

}
