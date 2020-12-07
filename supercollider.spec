# Modified from this spec: https://github.com/TristanCacqueray/supercollider-distgit/blob/master/supercollider.spec

Summary: Object oriented programming environment for real-time audio and video processing
Name: supercollider
Version: 3.11.2
Release: 1%{?dist}
License: GPLv2+
URL: https://supercollider.github.io/

Source0: https://github.com/supercollider/supercollider/releases/download/Version-%{version}/SuperCollider-%{version}-Source.tar.bz2

Requires: libsndfile
Requires: libXt
Requires: qt5-qtbase
Requires: qt5-qtwebengine
Requires: qt5-qtsvg
Requires: qt5-linguist
Requires: qt5-qtwebsockets
Requires: fftw

BuildRequires: cmake
BuildRequires: fftw3-devel
BuildRequires: gcc-c++
BuildRequires: jack-audio-connection-kit-devel
BuildRequires: libatomic
BuildRequires: libsndfile-devel
BuildRequires: libXt-devel
BuildRequires: make
BuildRequires: pkgconfig
BuildRequires: qt5-qtbase-devel
BuildRequires: qt5-qtwebengine-devel
BuildRequires: qt5-qtsvg-devel
BuildRequires: qt5-linguist
BuildRequires: qt5-qtwebsockets-devel
BuildRequires: systemd-devel

%description
SuperCollider is an object oriented programming environment for
real-time audio and video processing. It is one of the finest and most
versatile environments for signal processing and especially for
creating music applications of all kinds, such as complete
compositions, interactive performances, installations etc.

%package devel
Summary: Development files for SuperCollider
BuildArch: noarch
Requires: supercollider%{?_isa} = %{version}-%{release}
Requires: alsa-lib-devel
Requires: avahi-devel
Requires: boost-devel
Requires: jack-audio-connection-kit-devel
Requires: libsndfile-devel
Requires: pkgconfig

%description devel
This package includes include files and libraries needed to develop
SuperCollider applications

%prep
%setup -q -n SuperCollider-%{version}-Source

# Man får en segfault når man start sclang - nok på grund af LTO
# gdb peger på, at det er i qt det går galt:
# Thread 1 "sclang" received signal SIGSEGV, Segmentation fault.
# 0x00007ffff6e12d5b in void doActivate<false>(QObject*, int, void**) () from /lib64/libQt5Core.so.5
# https://bugzilla.redhat.com/show_bug.cgi?id=1872065
# https://bugzilla.redhat.com/show_bug.cgi?id=1789137
%define _lto_cflags %{nil}
%build
export CFLAGS="%{build_cflags} -fext-numeric-literals"
export CXXFLAGS="%{build_cxxflags} -fext-numeric-literals"
%cmake -DCMAKE_SKIP_RPATH:BOOL=ON -DSC_EL=OFF -DSC_ED=OFF -DCMAKE_BUILD_TYPE=Release -DSUPERNOVA=ON ..

# Since Fedora 33 cmake does out-of-source builds per default. To handle this %cmake_{build,install} should be used instead of %make_{build,install}
# https://fedoraproject.org/wiki/Changes/CMake_to_do_out-of-source_builds
%cmake_build

%install
%cmake_install
# install external header libraries needed to build external ugens
mkdir -p %{buildroot}/%{_includedir}/SuperCollider/external_libraries
cp -r external_libraries/nova* %{buildroot}/%{_includedir}/SuperCollider/external_libraries
# install the version file
install -m0644 SCVersion.txt %{buildroot}/%{_includedir}/SuperCollider/
# remove .placeholder file
find %{buildroot}/%{_datadir} -name .placeholder -delete

%files
%doc README*
%license COPYING
%{_bindir}/sclang
# in doc
%exclude %{_datadir}/SuperCollider/AUTHORS
%exclude %{_datadir}/SuperCollider/COPYING
%exclude %{_datadir}/SuperCollider/README.md
%exclude %{_datadir}/SuperCollider/README_LINUX.md
%exclude %{_datadir}/SuperCollider/CHANGELOG.md
%dir %{_datadir}/SuperCollider
%{_datadir}/SuperCollider/HelpSource
%{_datadir}/SuperCollider/SCClassLibrary
%{_datadir}/SuperCollider/sounds
%{_datadir}/SuperCollider/translations
%{_datadir}/pixmaps/supercollider*
# scsynth
%{_bindir}/scsynth
%dir %{_libdir}/SuperCollider
%{_libdir}/SuperCollider/plugins
%ifnarch %{arm}
# supernova
%{_bindir}/supernova
%endif
# examples
%{_datadir}/SuperCollider/examples
%exclude %{_datadir}/doc/SuperCollider/
%{_datadir}/SuperCollider/HID_Support
# ide
%{_bindir}/scide
%{_datadir}/applications/SuperColliderIDE.desktop
%{_datadir}/pixmaps/sc_ide.svg
%{_datadir}/mime/packages/supercollider.xml
%{_datadir}/SuperCollider/Extensions/scide_scvim/SCVim.sc

%files devel
%{_includedir}/SuperCollider
