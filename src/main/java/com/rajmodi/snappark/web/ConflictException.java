package com.rajmodi.snappark.web;

/** The request is valid but loses to the current state of the resource (HTTP 409). */
public class ConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ConflictException(String message) {
		super(message);
	}
}
