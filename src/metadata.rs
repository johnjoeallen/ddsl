use crate::ast::{Distro, Variant};
use crate::error::Diagnostic;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PackageManager {
    Apk,
    Apt,
    Dnf,
    None,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BaseMetadata {
    pub mutable: bool,
    pub package_manager: PackageManager,
    pub shell_available: bool,
}

pub fn metadata_for(distro: Distro, variant: Variant) -> Result<BaseMetadata, Diagnostic> {
    if distro == Distro::Windows {
        return Err(Diagnostic::new(
            "windows base images are not supported by the Dockerfile backend yet",
            None,
        ));
    }

    let package_manager = match distro {
        Distro::Wolfi | Distro::Alpine => PackageManager::Apk,
        Distro::Debian | Distro::Ubuntu => PackageManager::Apt,
        Distro::Rhel => PackageManager::Dnf,
        Distro::Distroless | Distro::Windows => PackageManager::None,
    };
    let shell_available = !matches!(distro, Distro::Distroless | Distro::Windows);
    let mutable = match variant {
        Variant::Dev => true,
        Variant::Runtime => false,
    };

    Ok(BaseMetadata {
        mutable,
        package_manager,
        shell_available,
    })
}

pub fn package_for_tool(tool: &str, distro: Distro) -> Option<&'static str> {
    match distro {
        Distro::Wolfi | Distro::Alpine => match tool {
            "git" => Some("git"),
            "curl" => Some("curl"),
            "aws-cli" => Some("aws-cli"),
            "ca-certificates" => Some("ca-certificates"),
            "openssl" => Some("openssl"),
            "build-base" => Some("build-base"),
            "unzip" => Some("unzip"),
            _ => None,
        },
        Distro::Debian | Distro::Ubuntu => match tool {
            "git" => Some("git"),
            "curl" => Some("curl"),
            "aws-cli" => Some("awscli"),
            "ca-certificates" => Some("ca-certificates"),
            "openssl" => Some("openssl"),
            "build-base" => Some("build-essential"),
            "unzip" => Some("unzip"),
            _ => None,
        },
        Distro::Rhel => match tool {
            "git" => Some("git"),
            "curl" => Some("curl"),
            "aws-cli" => Some("awscli"),
            "ca-certificates" => Some("ca-certificates"),
            "openssl" => Some("openssl"),
            "build-base" => Some("@development-tools"),
            "unzip" => Some("unzip"),
            _ => None,
        },
        Distro::Distroless | Distro::Windows => None,
    }
}

pub fn carried_tool_paths(tool: &str) -> Option<&'static [&'static str]> {
    match tool {
        "git" => Some(&["/usr/bin/git", "/usr/libexec/git-core"]),
        "ca-certificates" => Some(&["/etc/ssl/certs"]),
        "aws-cli" => Some(&["/usr/bin/aws", "/usr/lib/aws-cli"]),
        _ => None,
    }
}
