# Provider Resource Patterns

Use when creating or modifying provider resource classes (AWS, Azure, GCP) to ensure consistent patterns for property definitions, defaults, and validation.

## Core Patterns

### 1. Property Annotations

Every resource property must have a `@Property` annotation with a description:

```java
@Property(description = "The IPv4 CIDR block for the VPC (e.g., 10.0.0.0/16)")
private String cidrBlock;
```

### 2. Default Values via Field Initialization

Use Java field initialization for defaults, NOT annotation attributes:

```java
// CORRECT: Default via field initialization
@Property(description = "Enable DNS support in the VPC")
private Boolean enableDnsSupport = true;

@Property(description = "Instance tenancy option")
private String tenancy = "default";

// WRONG: Don't use annotation for defaults
@Property(description = "Enable DNS support", defaultValue = "true")  // NO!
private Boolean enableDnsSupport;
```

### 3. Valid Values (String Enums)

Use `validValues` for properties with a fixed set of allowed values:

```java
@Property(description = "Instance tenancy option",
          validValues = {"default", "dedicated", "host"})
private String tenancy = "default";

@Property(description = "Root volume type",
          validValues = {"gp2", "gp3", "io1", "io2", "st1", "sc1"})
private String rootVolumeType = "gp3";

@Property(description = "ACL type",
          validValues = {"private", "public-read", "public-read-write", "authenticated-read"})
private String acl = "private";
```

### 4. Required Properties

Mark essential properties as `optional = false`:

```java
@Property(description = "The AMI ID to use for the instance", optional = false)
private String ami;

@Property(description = "The IPv4 CIDR block for the VPC", optional = false)
private String cidrBlock;
```

### 5. Cloud-Managed Properties

Properties set by the cloud provider after creation use `@Cloud`:

```java
// User-configurable properties first
@Property(description = "The CIDR block", optional = false)
private String cidrBlock;

// Then cloud-managed properties with comment separator
// --- Cloud-managed properties (read-only) ---

@Cloud
@Property(description = "The VPC ID assigned by AWS")
private String vpcId;

@Cloud
@Property(description = "The current state of the VPC")
private String state;
```

## Complete Example

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Vpc")
public class VpcResource {

    @Property(description = "The IPv4 CIDR block (e.g., 10.0.0.0/16)")
    private String cidrBlock; // Required property because it is not initialized; same as @Property(optional = false) which can be omitted; only required for non @cloud properties

    @Property(description = "Tenancy option for instances",
              validValues = {"default", "dedicated"})
    private String instanceTenancy = "default";

    @Property(description = "Enable DNS support in the VPC")
    private Boolean enableDnsSupport = true;

    @Property(description = "Enable DNS hostnames in the VPC")
    private Boolean enableDnsHostnames = false;

    @Property(description = "Tags to apply to the VPC")
    private Map<String, String> tags;

    // --- Cloud-managed properties (read-only) ---

    @Cloud
    @Property(description = "The VPC ID assigned by AWS")
    private String vpcId;

    @Cloud
    @Property(description = "The current state (pending, available)")
    private String state;
}
```

## Package Organization

Resources are organized by domain:
- `networking/` - VPC, Subnet, SecurityGroup, NatGateway, RouteTable, etc.
- `compute/` - EC2Instance, etc.
- `storage/` - S3Bucket, EbsVolume, etc.
- `loadbalancing/` - LoadBalancer, Listener, TargetGroup
- `dns/` - HostedZone, RecordSet
- `core/` - ResourceGroup (Azure-specific)

## Kite Schema Syntax (Generated Output)

The Java resource classes generate Kite schema files with this syntax:

```kite
schema Vpc {
    string cidrBlock                              // Required - user must provide
    string instanceTenancy = "default"            // Optional - has default
    boolean enableDnsSupport = true               // Optional - has default
    map<string, string> tags                      // Optional - no default

    @cloud string vpcId                           // Cloud-generated, read-only
    @cloud string state                           // Cloud-generated, read-only
    @cloud(importable) string arn                 // Importable for existing resources
}
```

### Property Categories

| Syntax | Java Pattern | User Action |
|--------|--------------|-------------|
| `string name` | `@Property(optional = false)` | Required - must provide |
| `number port = 8080` | Field initialization | Optional - has default |
| `map<string, string> tags` | No initialization, no `optional = false` | Optional - no default |
| `@cloud string state` | `@Cloud` annotation | Read-only - cloud assigns |
| `@cloud(importable) string arn` | `@Cloud(importable = true)` | Read-only - can import existing |

### Schema to Java Mapping

```kite
// Kite Schema
schema Instance {
    string ami                       // Required
    string instanceType = "t3.micro" // Default
    @cloud string state              // Cloud-managed
    @cloud(importable) string arn    // Importable
}
```

```java
// Equivalent Java
@TypeName("Instance")
public class Ec2InstanceResource {
    @Property(description = "AMI ID", optional = false)
    private String ami;

    @Property(description = "Instance type")
    private String instanceType = "t3.micro";

    @Cloud
    @Property(description = "Instance state")
    private String state;

    @Cloud(importable = true)
    @Property(description = "Amazon Resource Name")
    private String arn;
}
```

### Importable Properties by Provider

**AWS** - Use `@Cloud(importable = true)` for:
- `arn` - Amazon Resource Name (most resources)
- `alias` - KMS key aliases, Lambda function aliases
- Resource-specific IDs when ARN not available

**Azure** - Use `@Cloud(importable = true)` for:
- `id` - Azure Resource ID (format: `/subscriptions/{sub}/resourceGroups/{rg}/providers/{provider}/{type}/{name}`)

### Valid Values in Schema

Properties with `validValues` generate schema comments:

```kite
schema aws_ec2_instance {
    // Valid: default, dedicated, host
    string tenancy = "default"

    // Valid: gp2, gp3, io1, io2, st1, sc1
    string rootVolumeType = "gp3"
}
```

## Checklist

Before committing a resource class:

- [ ] All properties have `@Property(description = "...")`
- [ ] Required properties have `optional = false`
- [ ] Properties with fixed values have `validValues = {...}`
- [ ] Defaults are set via field initialization (not annotation)
- [ ] Cloud-managed properties have `@Cloud` annotation
- [ ] Importable properties use `@Cloud(importable = true)` (ARN for AWS, id for Azure)
- [ ] Class has `@TypeName`, `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [ ] File is in correct domain package