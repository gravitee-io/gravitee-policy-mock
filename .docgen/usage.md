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
