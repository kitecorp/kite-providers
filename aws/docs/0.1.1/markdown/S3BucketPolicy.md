# S3BucketPolicy

## Example

```kite
resource S3BucketPolicy example {
    bucket = "example-value"
    policy = "example-value"
}
```

## Properties

| Name | Type | Default | Valid Values | Required | Description |
|------|------|---------|--------------|----------|-------------|
| `bucket` | `string` | — | — | No | The name of the bucket to apply the policy to |
| `policy` | `string` | — | — | No | The bucket policy as a JSON string (IAM policy document) |

[← Back to Index](README.md)
