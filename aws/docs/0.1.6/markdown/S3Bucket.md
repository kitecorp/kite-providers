# S3Bucket

## Example

```kite
resource S3Bucket example {
    bucket                       = "example-value"
    acl                          = "private"
    versioning                   = false
    region                       = "example-value"
    serverSideEncryption         = "AES256"
    kmsKeyId                     = "example-value"
    blockPublicAccess            = true
    websiteIndexDocument         = "example-value"
    websiteErrorDocument         = "example-value"
    websiteRedirectAllRequestsTo = "example-value"
    corsRules                    = "..."
    lifecycleRules               = "..."
    logging                      = "..."
    tags                         = "..."
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `bucket` | `string` | — | — | No | Bucket name (globally unique, DNS-compliant) |
| `acl` | `string` | `private` | `private`, `public-read`, `public-read-write`, `authenticated-read` | No | Canned ACL for the bucket |
| `versioning` | `boolean` | `false` | — | No | Enable versioning for the bucket |
| `region` | `string` | — | — | No | AWS region for the bucket |
| `serverSideEncryption` | `string` | — | `AES256`, `aws:kms` | No | Server-side encryption algorithm |
| `kmsKeyId` | `string` | — | — | No | KMS key ID for encryption (when using aws:kms) |
| `blockPublicAccess` | `boolean` | `true` | — | No | Block all public access |
| `websiteIndexDocument` | `string` | — | — | No | Index document for static website hosting |
| `websiteErrorDocument` | `string` | — | — | No | Error document for static website hosting |
| `websiteRedirectAllRequestsTo` | `string` | — | — | No | Redirect all requests to another host |
| `corsRules` | `any[]` | — | — | No | CORS configuration rules |
| `lifecycleRules` | `any[]` | — | — | No | Lifecycle rules for object management |
| `logging` | `any` | — | — | No | Access logging configuration |
| `tags` | `object` | — | — | No | Tags to apply to the bucket |
## Cloud Properties

_These properties are set by the cloud provider after resource creation._

| Name | Type | Description |
|------|------|-------------|
| `arn` | `string` | *📥 importable* The ARN of the bucket |
| `domainName` | `string` | The domain name of the bucket |
| `regionalDomainName` | `string` | The regional domain name of the bucket |
| `websiteEndpoint` | `string` | Website endpoint (if hosting enabled) |
| `creationDate` | `string` | The creation date of the bucket |

[← Back to Index](README.md)
