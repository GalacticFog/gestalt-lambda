#!/bin/bash

#mono
apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 3FA7E0328081BFF6A14DA29AA6A19B38D3D831EF
echo "deb http://download.mono-project.com/repo/debian wheezy main" |  tee /etc/apt/sources.list.d/mono-xamarin.list
echo "deb http://download.mono-project.com/repo/debian wheezy-apache24-compat main" |  tee -a /etc/apt/sources.list.d/mono-xamarin.list
apt-get update -y 
apt-get install -y mono-complete

#libuv
apt-get install -y automake libtool curl
curl -sSL https://github.com/libuv/libuv/archive/v1.4.2.tar.gz |  tar zxfv - -C /usr/local/src
cd /usr/local/src/libuv-1.4.2
sh autogen.sh
./configure
make 
make install
rm -rf /usr/local/src/libuv-1.4.2 && cd ~/
ldconfig

#dnvm
apt-get install -y unzip
curl -sSL https://raw.githubusercontent.com/aspnet/Home/dev/dnvminstall.sh | DNX_BRANCH=dev sh && source ~/.dnx/dnvm/dnvm.sh

#.net core dependencies
apt-get install -y libunwind8 libssl-dev
mozroots --import --sync

#core runtime
dnvm upgrade -u
dnvm install latest -r coreclr -u -p

#additional
apt-get install -y libcurl4-openssl-dev
