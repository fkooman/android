/*
 *  This file is part of eduVPN.
 *
 *     eduVPN is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     eduVPN is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with eduVPN.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.eduvpn.app.service

import io.reactivex.Single
import io.reactivex.SingleSource
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import nl.eduvpn.app.BuildConfig
import nl.eduvpn.app.Constants
import nl.eduvpn.app.entity.Instance
import nl.eduvpn.app.entity.OrganizationList
import nl.eduvpn.app.entity.ServerList
import nl.eduvpn.app.entity.exception.InvalidSignatureException
import nl.eduvpn.app.utils.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Callable

/**
 * Service which provides the configurations for organization related data model.
 * Created by Daniel Zolnai on 2016-10-07.
 */
class OrganizationService(private val serializerService: SerializerService,
                          private val securityService: SecurityService,
                          private val okHttpClient: OkHttpClient) {


    fun fetchServerList() : Single<ServerList> {
        val serverListUrl = BuildConfig.ORGANIZATION_LIST_BASE_URL + "server_list.json"
        val serverListObservable = createGetJsonSingle(serverListUrl)
        val signatureObservable = createSignatureSingle(serverListUrl)
        return Single.zip(serverListObservable, signatureObservable, BiFunction<String, String, ServerList> { serverList: String, signature: String ->
            try {
                if (securityService.verifyMinisign(serverList, signature)) {
                    val organizationListJson = JSONObject(serverList)
                    return@BiFunction serializerService.deserializeServerList(organizationListJson)
                } else {
                    throw InvalidSignatureException("Signature validation failed for server list!")
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Unable to verify signature", ex)
                throw InvalidSignatureException("Signature validation failed for server list!")
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

    }

    fun fetchOrganizations(): Single<OrganizationList> {
        val listUrl = BuildConfig.ORGANIZATION_LIST_BASE_URL + "organization_list.json"
        val organizationListObservable = createGetJsonSingle(listUrl)
        val signatureObservable = createSignatureSingle(listUrl)
        return Single.zip(organizationListObservable, signatureObservable, BiFunction<String, String, OrganizationList> { organizationList: String, signature: String ->
            try {
                if (securityService.verifyMinisign(organizationList, signature)) {
                    val organizationListJson = JSONObject(organizationList)
                    return@BiFunction serializerService.deserializeOrganizationList(organizationListJson)
                } else {
                    throw InvalidSignatureException("Signature validation failed for organization list!")
                }
            } catch (ex: Exception) {
                Log.w(TAG, "Unable to verify signature", ex)
                throw InvalidSignatureException("Signature validation failed for organization list!")
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    private fun createSignatureSingle(signatureRequestUrl: String): Single<String> {
        return Single.defer(Callable<SingleSource<String>> {
            val postfixedUrl = signatureRequestUrl + BuildConfig.SIGNATURE_URL_POSTFIX
            val request = Request.Builder().url(postfixedUrl).build()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body
            if (responseBody != null) {
                val result = responseBody.string()
                responseBody.close()
                return@Callable Single.just(result)
            } else {
                return@Callable Single.error<String>(IOException("Response body is empty!"))
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    private fun createGetJsonSingle(url: String): Single<String> {
        return Single.defer(Callable<SingleSource<String>> {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body
            val responseCode = response.code
            var isGone = false
            for (code in Constants.GONE_HTTP_CODES) {
                if (responseCode == code) {
                    isGone = true
                }
            }
            if (isGone) {
                return@Callable Single.error(OrganizationDeletedException())
            } else if (responseBody != null) {
                val result = responseBody.string()
                responseBody.close()
                return@Callable Single.just(result)
            } else {
                return@Callable Single.error(IOException("Response body is empty!"))
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    class OrganizationDeletedException : IllegalStateException()

    companion object {
        private val TAG = OrganizationService::class.java.name
    }
}