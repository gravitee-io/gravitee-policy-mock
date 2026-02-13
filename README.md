
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# Mock Policy

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-mock/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-mock/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-mock/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-mock.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-mock)

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




## Phases
The `mock` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MCP PROXY`

### Supported flow phases:

* Request

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM |
| --- | ---  |
|2.0.0 and after|4.11 and after |
|1.14.0 to 1.15.x|4.1.24 and after |
|Up to 1.13.5|All supported versions |


## Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Response body<br>`content`| string|  | The body content of the mock response. Supports Expression Language for dynamic values.|
| Headers<br>`headers`| array|  | Custom HTTP headers to include in the mock response. Header values support Expression Language.<br/>See "Headers" section.|
| HTTP Status Code<br>`status`| enum (string)| ✅| HTTP status code returned to the consumer in the mock response.<br>Values: `100` `101` `102` `200` `201` `202` `203` `204` `205` `206` `207` `300` `301` `302` `303` `304` `305` `307` `400` `401` `402` `403` `404` `405` `406` `407` `408` `409` `410` `411` `412` `413` `414` `415` `416` `417` `422` `423` `424` `429` `500` `501` `502` `503` `504` `505` `507`|


#### Headers (Array)
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Description  |
|:----------------------|:-----------------------|:----------:|:-------------|
| Name<br>`name`| string|  | Name of the header|
| Value<br>`value`| string|  | Value of the header (support EL)|




## Examples

*Mock a JSON response with Expression Language*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "Mock Policy example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "Mock Policy",
            "enabled": true,
            "policy": "mock",
            "configuration":
              {
                  "status": "200",
                  "headers": [
                      {
                          "name": "Content-Type",
                          "value": "application/json"
                      },
                      {
                          "name": "Server",
                          "value": "Gravitee.io"
                      }
                  ],
                  "content": "{\"id\": \"{#request.paths[3]}\", \"name\": \"{#properties['name']}\"}"
              }
          }
        ]
      }
    ]
  }
}

```
*Mock a JSON response on MCP Proxy API*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "MCP_PROXY",
    "name": "Mock Policy example API",
    "flows": [
      {
        "name": "All plans flow",
        "enabled": true,
        "selectors": [
          {
            "type": "MCP",
             "methods" : []
          }
        ],
        "request": [
          {
            "name": "Mock Policy",
            "enabled": true,
            "policy": "mock",
            "configuration":
              {
                  "status": "200",
                  "headers": [
                      {
                          "name": "Content-Type",
                          "value": "application/json"
                      },
                      {
                          "name": "Server",
                          "value": "Gravitee.io"
                      }
                  ],
                  "content": "{\"id\": \"{#request.paths[3]}\", \"name\": \"{#properties['name']}\"}"
              }
          }
        ]
      }
    ]
  }
}

```


## Changelog

### [1.15.0](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.14.2...1.15.0) (2025-12-11)


##### Features

* enable for MCP Proxy API ([d4c0957](https://github.com/gravitee-io/gravitee-policy-mock/commit/d4c0957eb2f52c4bc753460173f100d7ac9edce6))

### [1.15.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.14.2...1.15.0-alpha.1) (2025-11-07)


##### Features

* enable for MCP Proxy API ([c58383b](https://github.com/gravitee-io/gravitee-policy-mock/commit/c58383bda293b63f7efa4c7933fca5828006d64b))

#### [1.14.2](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.14.1...1.14.2) (2025-07-06)


##### Bug Fixes

* ensure example values with 'string' + 'date-time' format are handled correctly ([1c31dea](https://github.com/gravitee-io/gravitee-policy-mock/commit/1c31dea2d037f08096ab7a71cbcf7ec84f2f7327))

#### [1.14.1](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.14.0...1.14.1) (2025-03-11)


##### Bug Fixes

* rework response example extractor from schema ([9915115](https://github.com/gravitee-io/gravitee-policy-mock/commit/9915115fb013d1e175b705ec29b49f40da99be8a))

### [1.14.0](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.5...1.14.0) (2024-09-18)


##### Features

* support mock policy on v4 proxy API request phase ([8251445](https://github.com/gravitee-io/gravitee-policy-mock/commit/825144534bc16c1d21218453b9dc1f6f471f5b43))
* update schema-form for v4 policy studio ([ce1db74](https://github.com/gravitee-io/gravitee-policy-mock/commit/ce1db7436ebaa193d847623397fcf17bbfc95987))

#### [1.13.5](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.4...1.13.5) (2023-10-31)


##### Bug Fixes

* jsonSchema - config code editor mode for new policy studio display ([d13ba38](https://github.com/gravitee-io/gravitee-policy-mock/commit/d13ba389ae7138f570e63efde848a72a23c40de2))

#### [1.13.4](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.3...1.13.4) (2023-09-28)


##### Bug Fixes

* handle array in response ([1f97ed8](https://github.com/gravitee-io/gravitee-policy-mock/commit/1f97ed82538fa162254762e3aad76507b0eb15df))

#### [1.13.3](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.2...1.13.3) (2023-09-27)


##### Bug Fixes

* handle no components in OpenAPI descriptor ([49904ac](https://github.com/gravitee-io/gravitee-policy-mock/commit/49904acccec6e668a0448475416ad36ab4198085))

#### [1.13.2](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.1...1.13.2) (2023-07-20)


##### Bug Fixes

* **deps:** bump dependency ([6bcf31d](https://github.com/gravitee-io/gravitee-policy-mock/commit/6bcf31d487ada2ec797e5f8c3a456490e7b718eb))

#### [1.13.1](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.13.0...1.13.1) (2023-07-20)


##### Bug Fixes

* update policy description ([77dae49](https://github.com/gravitee-io/gravitee-policy-mock/commit/77dae49cc792dcbdb2ce56d8f9e838a35be9a23f))

### [1.13.0](https://github.com/gravitee-io/gravitee-policy-mock/compare/1.12.0...1.13.0) (2022-01-21)


##### Features

* **headers:** Internal rework and introduce HTTP Headers API ([93f1afa](https://github.com/gravitee-io/gravitee-policy-mock/commit/93f1afa3fdc207a9248e957fccaf26f0f3296902)), closes [gravitee-io/issues#6772](https://github.com/gravitee-io/issues/issues/6772)

