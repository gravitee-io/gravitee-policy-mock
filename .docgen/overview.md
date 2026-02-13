You can use the Mock policy to create mock responses when a consumer calls one of your services.
This means you do not have to provide a functional backend as soon as you create your API, giving you more time to think about your API contract.

You can think of the policy as a contract-first approach — you are able to create a fully-functional API without needing to write a single line of code to handle consumer calls.

Internally, this policy replaces the default HTTP invoker with a mock invoker. There are no more HTTP calls between the gateway and a remote service or backend.

> **Note:** The Mock policy will *not* cause the other policies to be skipped, regardless of its location in the flow.

When defining the response body content, you can use Expression Language to provide a dynamic mock response.
