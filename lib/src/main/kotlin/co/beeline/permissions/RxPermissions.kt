package co.beeline.permissions

import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject

/**
 * Observe Android runtime permissions

 * Note: This class should be a singleton.
 */
class RxPermissions(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 8712
    }

    private val subjects: MutableMap<String, BehaviorSubject<Boolean>> = HashMap(6)

    /**
     * @return an Observable that emits the state changes for a given permission
     */
    @MainThread
    fun observe(permission: String): Observable<Boolean> {
        return subjectForPermission(permission)
    }

    /**
     * @return a Completable that completes when a given permission is granted
     */
    @MainThread
    fun onGranted(permission: String): Completable {
        return observe(permission).filter { it }.take(1).ignoreElements()
    }

    /**
     * @return a Single that emits the result of the request
     */
    @MainThread
    fun request(vararg permissions: String, activity: Activity): Single<Boolean> {
        return request(*permissions) {
            ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE)
        }
    }

    /**
     * @return a Single that emits the result of the request
     */
    @MainThread
    fun request(vararg permissions: String, fragment: Fragment): Single<Boolean> {
        return request(*permissions) {
            fragment.requestPermissions(permissions, REQUEST_CODE)
        }
    }

    @MainThread
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        if (requestCode == REQUEST_CODE) {
            for (index in permissions.indices) {
                val result = grantResults[index] == PackageManager.PERMISSION_GRANTED
                subjectForPermission(permissions[index]).onNext(result)
            }
            return true
        }
        return false
    }

    fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.fold(true) { all, permission -> all && hasPermission(permission) }
    }

    fun hasPermission(permission: String): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isGranted(permission)
    }

    private fun request(vararg permissions: String, request: () -> Unit): Single<Boolean> {
        return if (hasPermissions(*permissions)) Single.just(true)
        else {
            Observable
                    .combineLatest(
                            permissions.map { observe(it).skip(1).take(1) }
                    ) { results ->
                        results.fold(true) { all, result -> all && (result as Boolean) }
                    }
                    .take(1).singleOrError()
                    .doOnSubscribe { request() }
        }
    }

    private fun subjectForPermission(permission: String): BehaviorSubject<Boolean> {
        var subject = subjects[permission]
        if (subject == null) {
            subject = BehaviorSubject.createDefault(hasPermission(permission))
            subjects[permission] = subject
            return subject
        }
        return subject
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun isGranted(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
