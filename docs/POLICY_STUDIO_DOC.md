## Overview
You can use the Mock policy to create mock responses when a consumer calls one of your services.
This means you do not have to provide a functional backend as soon as you create your API, giving you more time to think about your API contract.

You can think of the policy as a contract-first approach — you are able to create a fully-functional API without needing to write a single line of code to handle consumer calls.

Internally, this policy replaces the default HTTP invoker with a mock invoker. There are no more HTTP calls between the gateway and a remote service or backend.

> **Note:** The Mock policy will *not* cause the other policies to be skipped, regardless of its location in the flow.

When defining the response body content, you can use Expression Language to provide a dynamic mock response.



## Usage
### Expression Language support

The response body content and header values support Gravitee Expression Language (EL). This allows you to create dynamic mock responses based on request context.

Available EL variables include:
* `{#request.paths[n]}` — Access path segments
* `{#request.headers['X-Custom']}` — Access request headers
* `{#properties['key']}` — Access API properties

#### Body content example (JSON)

```json
{
    "id": "{#request.paths[3]}",
    "firstname": "{#properties['firstname_' + #request.paths[3]]}",
    "lastname": "{#properties['lastname_' + #request.paths[3]]}",
    "age": {(T(java.lang.Math).random() * 60).intValue()},
    "createdAt": {(new java.util.Date()).getTime()}
}
```

#### Body content example (XML)

```xml
<user id="{#request.paths[3]}">
    <firstname>{#properties['firstname_' + #request.paths[3]]}</firstname>
    <lastname>{#properties['lastname_' + #request.paths[3]]}</lastname>
    <age>{(T(java.lang.Math).random() * 60).intValue()}</age>
    <createdAt>{(new java.util.Date()).getTime()}</createdAt>
</user>
```

### Automatic content-type detection

You don't need to provide the `Content-Type` header. The Mock policy automatically detects the content type of the response body (JSON, XML, or plain text).



