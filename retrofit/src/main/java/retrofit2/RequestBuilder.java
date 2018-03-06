/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.io.IOException;
import javax.annotation.Nullable;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.BufferedSink;

final class RequestBuilder {
  private final String method;

  private final HttpUrl baseUrl;
  private @Nullable String relativeUrl;

  private final Request.Builder requestBuilder;
  private @Nullable MediaType contentType;

  private final boolean hasBody;
  private @Nullable MultipartBody.Builder multipartBuilder;
  private @Nullable FormBody.Builder formBuilder;
  private @Nullable RequestBody body;

  RequestBuilder(String method, HttpUrl baseUrl, @Nullable String relativeUrl,
      @Nullable Headers headers, @Nullable MediaType contentType, boolean hasBody,
      boolean isFormEncoded, boolean isMultipart) {
    this.method = method;
    this.baseUrl = baseUrl;
    this.relativeUrl = relativeUrl;
    this.requestBuilder = new Request.Builder();
    this.contentType = contentType;
    this.hasBody = hasBody;

    if (headers != null) {
      requestBuilder.headers(headers);
    }

    if (isFormEncoded) {
      // Will be set to 'body' in 'build'.
      formBuilder = new FormBody.Builder();
    } else if (isMultipart) {
      // Will be set to 'body' in 'build'.
      multipartBuilder = new MultipartBody.Builder();
      multipartBuilder.setType(MultipartBody.FORM);
    }
  }

  void setRelativeUrl(Object relativeUrl) {
    this.relativeUrl = relativeUrl.toString();
  }

  void addHeader(String name, String value) {
    if ("Content-Type".equalsIgnoreCase(name)) {
      MediaType type = MediaType.parse(value);
      if (type == null) {
        throw new IllegalArgumentException("Malformed content type: " + value);
      }
      contentType = type;
    } else {
      requestBuilder.addHeader(name, value);
    }
  }

  @SuppressWarnings("ConstantConditions") // Only called when isFormEncoded was true.
  void addFormField(String name, String value, boolean encoded) {
    if (encoded) {
      formBuilder.addEncoded(name, value);
    } else {
      formBuilder.add(name, value);
    }
  }

  @SuppressWarnings("ConstantConditions") // Only called when isMultipart was true.
  void addPart(Headers headers, RequestBody body) {
    multipartBuilder.addPart(headers, body);
  }

  @SuppressWarnings("ConstantConditions") // Only called when isMultipart was true.
  void addPart(MultipartBody.Part part) {
    multipartBuilder.addPart(part);
  }

  void setBody(RequestBody body) {
    this.body = body;
  }

  Request build() {
    HttpUrl url = baseUrl.resolve(relativeUrl);
    if (url == null) {
      throw new IllegalArgumentException(
          "Malformed URL. Base: " + baseUrl + ", Relative: " + relativeUrl);
    }

    RequestBody body = this.body;
    if (body == null) {
      // Try to pull from one of the builders.
      if (formBuilder != null) {
        body = formBuilder.build();
      } else if (multipartBuilder != null) {
        body = multipartBuilder.build();
      } else if (hasBody) {
        // Body is absent, make an empty body.
        body = RequestBody.create(null, new byte[0]);
      }
    }

    MediaType contentType = this.contentType;
    if (contentType != null) {
      if (body != null) {
        body = new ContentTypeOverridingRequestBody(body, contentType);
      } else {
        requestBuilder.addHeader("Content-Type", contentType.toString());
      }
    }

    return requestBuilder
        .url(url)
        .method(method, body)
        .build();
  }

  private static class ContentTypeOverridingRequestBody extends RequestBody {
    private final RequestBody delegate;
    private final MediaType contentType;

    ContentTypeOverridingRequestBody(RequestBody delegate, MediaType contentType) {
      this.delegate = delegate;
      this.contentType = contentType;
    }

    @Override public MediaType contentType() {
      return contentType;
    }

    @Override public long contentLength() throws IOException {
      return delegate.contentLength();
    }

    @Override public void writeTo(BufferedSink sink) throws IOException {
      delegate.writeTo(sink);
    }
  }
}
