import { writeFileSync, readFileSync, existsSync } from 'fs';
import { execSync } from 'child_process';


// Run in node to update the OIE API type definitions
// Transforms OIE API type definitions into a format usable by tools

let apis;
const apiRawPath = 'api_raw.json';

try {
    // fetch from http://localhost:8080/api/openapi.json to api_raw.json
    const apiTextResp = await fetch('http://localhost:8080/api/openapi.json');
    if (!apiTextResp.ok) {
        throw new Error(`Failed to fetch API definition: ${apiTextResp.status} ${apiTextResp.statusText}`);
    }
    const apiText = await apiTextResp.text();
    apis = JSON.parse(apiText);

    const apiRawPretty = JSON.stringify(apis, null, 2);
    writeFileSync(apiRawPath, apiRawPretty, 'utf-8');
} catch (e) {
    console.warn('Failed to fetch API definition from localhost, using existing api_raw.json');
    if (existsSync(apiRawPath)) {
        const apiText = readFileSync(apiRawPath, 'utf-8');
        apis = JSON.parse(apiText);
    } else {
        throw e;
    }
}

// Strip examples that don't exist from the file
function stripExamples(obj) {
    if (typeof obj !== 'object' || obj === null) {
        return;
    }

    if ("examples" in obj) {
        delete obj.examples;
    }

    if ("responses" in obj) {
        // If only a default key exists, create a 2xx key with the same content
        const responses = obj.responses;
        if (Object.keys(responses).length === 1 && "default" in responses) {
            // Deep copy the default response to avoid reference issues
            responses["2XX"] = JSON.parse(JSON.stringify(responses["default"]));
            // Ensure description exists for 2XX response (required by OpenAPI spec)
            if (!responses["2XX"].description) {
                responses["2XX"].description = "Successful response";
            }
            // Ensure description exists for default response as well
            if (!responses["default"].description) {
                responses["default"].description = "Default response";
            }
        }
    }

    // Ensure all responses have a description (required by OpenAPI spec)
    if ("responses" in obj) {
        for (const code in obj.responses) {
            if (!obj.responses[code].description) {
                obj.responses[code].description = "Response description";
            }
        }
    }

    // Fix schema type mismatch: 'array' type should not contain 'properties' field
    if (obj.type === 'array' && obj.properties) {
        delete obj.properties;
    }

    for (const key in obj) {
        stripExamples(obj[key]);
    }
}
stripExamples(apis);

// Remove duplicate paths that differ only by parameter name
if (apis.paths) {
    const paths = apis.paths;
    if (paths['/users/{userId}'] && paths['/users/{userIdOrName}']) {
        delete paths['/users/{userIdOrName}'];
    }
}

// Add global security definition if missing
if (!apis.security) {
    apis.security = [];
}

// Add security schemes if missing
if (!apis.components) {
    apis.components = {};
}
if (!apis.components.securitySchemes) {
    apis.components.securitySchemes = {
        "basicAuth": {
            "type": "http",
            "scheme": "basic"
        }
    };
}

// Apply security to all operations that don't have it
if (apis.paths) {
    for (const pathKey in apis.paths) {
        const pathItem = apis.paths[pathKey];
        for (const method in pathItem) {
            if (method === 'parameters' || method === 'summary' || method === 'description') continue;
            
            const operation = pathItem[method];
            if (!operation.security) {
                operation.security = [
                    {
                        "basicAuth": []
                    }
                ];
            }
        }
    }
}



// Write the cleaned API definition to a file
const apiCleanedPath = 'api_cleaned.json';
const apiCleanedPretty = JSON.stringify(apis, null, 2);
writeFileSync(apiCleanedPath, apiCleanedPretty, 'utf-8');

// Call npx swagger-cli bundle to bundle the cleaned API definition
const bundledApiPath = 'api_bundled.json';
execSync(`npx @redocly/cli bundle "${apiCleanedPath}" -o "${bundledApiPath}"`, { stdio: 'inherit' });

// Call npx openapi-typescript to generate TypeScript types from the bundled API definition
const typesOutputPath = 'index.d.ts';
execSync(`npx openapi-typescript "${bundledApiPath}" --output "${typesOutputPath}"`, { stdio: 'inherit' });
