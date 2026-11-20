#include <jni.h>
#include <stddef.h>

extern int etoile_lite_convert(const char *input, const char *aff,
                               const char *scene, char *error, int capacity);

JNIEXPORT jint JNICALL
Java_com_zeerqi27_etoile_1bridge_AndroidEtoileLite_nativeConvert(
    JNIEnv *env, jobject receiver, jbyteArray input, jbyteArray aff,
    jbyteArray scene, jbyteArray error) {
    (void)receiver;
    jint result = 1;
    const jsize capacity = (*env)->GetArrayLength(env, error);
    jbyte *input_bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (!input_bytes) return result;
    jbyte *aff_bytes = (*env)->GetByteArrayElements(env, aff, NULL);
    if (!aff_bytes) goto release_input;
    jbyte *scene_bytes = (*env)->GetByteArrayElements(env, scene, NULL);
    if (!scene_bytes) goto release_aff;
    jbyte *error_bytes = (*env)->GetByteArrayElements(env, error, NULL);
    if (error_bytes) {
        result = etoile_lite_convert((const char *)input_bytes, (const char *)aff_bytes,
                                     (const char *)scene_bytes, (char *)error_bytes, capacity);
        (*env)->ReleaseByteArrayElements(env, error, error_bytes, 0);
    }
    (*env)->ReleaseByteArrayElements(env, scene, scene_bytes, JNI_ABORT);
release_aff:
    (*env)->ReleaseByteArrayElements(env, aff, aff_bytes, JNI_ABORT);
release_input:
    (*env)->ReleaseByteArrayElements(env, input, input_bytes, JNI_ABORT);
    return result;
}
