package com.lansoftprogramming.runeSequence.ui.overlay;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.unix.X11;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * Enables true click-through overlays on Windows (WS_EX_TRANSPARENT).
 * <p>
 * Pure Swing/AWT cannot create click-through windows in a portable way; this uses
 * standard native APIs via JNA when running on Windows/macOS/X11.
 */
public final class ClickThroughWindowSupport {
	private static final Logger logger = LoggerFactory.getLogger(ClickThroughWindowSupport.class);
	private static final Platform PLATFORM = detectPlatform();

	private ClickThroughWindowSupport() {
	}

	public static boolean enable(Window window) {
		if (window == null || GraphicsEnvironment.isHeadless()) {
			return false;
		}
		return switch (PLATFORM) {
			case WINDOWS -> enableWindows(window);
			case MAC -> enableMac(window);
			case X11 -> enableX11(window);
			default -> false;
		};
	}

	public static boolean disable(Window window) {
		if (window == null || GraphicsEnvironment.isHeadless()) {
			return false;
		}
		return switch (PLATFORM) {
			case WINDOWS -> disableWindows(window);
			case MAC -> disableMac(window);
			case X11 -> disableX11(window);
			default -> false;
		};
	}

	private static boolean enableWindows(Window window) {
		try {
			if (!window.isDisplayable()) {
				window.addNotify();
			}

			Pointer pointer = Native.getComponentPointer(window);
			if (pointer == null) {
				return false;
			}

			HWND hwnd = new HWND(pointer);
			BaseTSD.LONG_PTR exStyle = User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE);
			long current = exStyle != null ? exStyle.longValue() : 0L;

			long desired = current | WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
			if (desired == current) {
				return true;
			}

			User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE, Pointer.createConstant(desired));
			User32.INSTANCE.SetWindowPos(
					hwnd,
					null,
					0, 0, 0, 0,
					WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER | WinUser.SWP_FRAMECHANGED | WinUser.SWP_NOACTIVATE
			);
			return true;
		} catch (Throwable t) {
			logger.debug("ClickThroughWindowSupport.enableWindows failed", t);
			return false;
		}
	}

	private static boolean disableWindows(Window window) {
		try {
			if (!window.isDisplayable()) {
				window.addNotify();
			}

			Pointer pointer = Native.getComponentPointer(window);
			if (pointer == null) {
				return false;
			}

			HWND hwnd = new HWND(pointer);
			BaseTSD.LONG_PTR exStyle = User32.INSTANCE.GetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE);
			long current = exStyle != null ? exStyle.longValue() : 0L;

			long desired = current & ~WinUser.WS_EX_TRANSPARENT;
			if (desired == current) {
				return true;
			}

			User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_EXSTYLE, Pointer.createConstant(desired));
			User32.INSTANCE.SetWindowPos(
					hwnd,
					null,
					0, 0, 0, 0,
					WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOZORDER | WinUser.SWP_FRAMECHANGED | WinUser.SWP_NOACTIVATE
			);
			return true;
		} catch (Throwable t) {
			logger.debug("ClickThroughWindowSupport.disableWindows failed", t);
			return false;
		}
	}

	private static boolean enableMac(Window window) {
		try {
			Pointer nsWindow = resolveMacNsWindow(window);
			if (nsWindow == null) {
				return false;
			}
			Objc.sendVoid(nsWindow, "setIgnoresMouseEvents:", (byte) 1);
			return true;
		} catch (Throwable t) {
			logger.debug("ClickThroughWindowSupport.enableMac failed", t);
			return false;
		}
	}

	private static boolean disableMac(Window window) {
		try {
			Pointer nsWindow = resolveMacNsWindow(window);
			if (nsWindow == null) {
				return false;
			}
			Objc.sendVoid(nsWindow, "setIgnoresMouseEvents:", (byte) 0);
			return true;
		} catch (Throwable t) {
			logger.debug("ClickThroughWindowSupport.disableMac failed", t);
			return false;
		}
	}

	private static Pointer resolveMacNsWindow(Window window) {
		if (!window.isDisplayable()) {
			window.addNotify();
		}
		Pointer componentPtr = Native.getComponentPointer(window);
		if (componentPtr == null) {
			return null;
		}
		Pointer candidate = Objc.sendPointer(componentPtr, "window");
		return candidate != null ? candidate : componentPtr;
	}

	private static boolean enableX11(Window window) {
		return setX11InputShape(window, true);
	}

	private static boolean disableX11(Window window) {
		return setX11InputShape(window, false);
	}

	private static boolean setX11InputShape(Window window, boolean clickThrough) {
		X11.Display display = null;
		X11.Pixmap bitmap = null;
		try {
			if (!window.isDisplayable()) {
				window.addNotify();
			}

			Pointer pointer = Native.getComponentPointer(window);
			if (pointer == null) {
				return false;
			}
			long xid = Pointer.nativeValue(pointer);
			if (xid == 0L) {
				return false;
			}
			X11.Window xWindow = new X11.Window(xid);

			display = X11.INSTANCE.XOpenDisplay(null);
			if (display == null) {
				return false;
			}

			if (clickThrough) {
				com.sun.jna.Memory empty = new com.sun.jna.Memory(1);
				empty.setByte(0, (byte) 0);
				bitmap = X11.INSTANCE.XCreateBitmapFromData(display, xWindow, empty, 1, 1);
				X11.Xext.INSTANCE.XShapeCombineMask(display, xWindow, X11.Xext.ShapeInput, 0, 0, bitmap, X11.Xext.ShapeSet);
			} else {
				X11.Xext.INSTANCE.XShapeCombineMask(display, xWindow, X11.Xext.ShapeInput, 0, 0, X11.Pixmap.None, X11.Xext.ShapeSet);
			}

			X11.INSTANCE.XFlush(display);
			return true;
		} catch (Throwable t) {
			logger.debug("ClickThroughWindowSupport.setX11InputShape failed", t);
			return false;
		} finally {
			try {
				if (display != null && bitmap != null && bitmap.longValue() != 0L) {
					X11.INSTANCE.XFreePixmap(display, bitmap);
				}
			} catch (Throwable ignored) {
			}
			try {
				if (display != null) {
					X11.INSTANCE.XCloseDisplay(display);
				}
			} catch (Throwable ignored) {
			}
		}
	}

	private static Platform detectPlatform() {
		String osName = System.getProperty("os.name");
		String name = osName != null ? osName.toLowerCase() : "";
		if (name.contains("win")) {
			return Platform.WINDOWS;
		}
		if (name.contains("mac")) {
			return Platform.MAC;
		}
		if (name.contains("nix") || name.contains("nux") || name.contains("aix")) {
			return Platform.X11;
		}
		return Platform.OTHER;
	}

	private enum Platform {
		WINDOWS,
		MAC,
		X11,
		OTHER
	}

	private static final class Objc {
		private static final com.sun.jna.NativeLibrary objc = loadObjcOrNull();
		private static final com.sun.jna.Function selRegisterName = objc != null ? objc.getFunction("sel_registerName") : null;
		private static final com.sun.jna.Function objcMsgSend = objc != null ? objc.getFunction("objc_msgSend") : null;

		static Pointer sendPointer(Pointer receiver, String selector) {
			if (receiver == null || selector == null || objcMsgSend == null || selRegisterName == null) {
				return null;
			}
			Pointer sel = (Pointer) selRegisterName.invoke(Pointer.class, new Object[]{selector});
			return (Pointer) objcMsgSend.invoke(Pointer.class, new Object[]{receiver, sel});
		}

		static void sendVoid(Pointer receiver, String selector, byte arg) {
			if (receiver == null || selector == null || objcMsgSend == null || selRegisterName == null) {
				return;
			}
			Pointer sel = (Pointer) selRegisterName.invoke(Pointer.class, new Object[]{selector});
			objcMsgSend.invoke(Void.class, new Object[]{receiver, sel, arg});
		}

		private static com.sun.jna.NativeLibrary loadObjcOrNull() {
			try {
				return com.sun.jna.NativeLibrary.getInstance("objc");
			} catch (Throwable ignored) {
				return null;
			}
		}
	}
}