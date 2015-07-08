LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    src/com/android/packageinstaller/EventLogTags.logtags

LOCAL_STATIC_JAVA_LIBRARIES += android-support-v4

LOCAL_PACKAGE_NAME := PackageInstaller
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

PRE_BOOT_LISTENER ?= $(LOCAL_PATH)/pre_boot_listener

include $(PRE_BOOT_LISTENER)/preboot.mk

include $(BUILD_PACKAGE)
