#!/usr/bin/env ruby

require 'rubygems'
require 'fileutils'

deploy_dir = "_deploy"

repo_url = `git remote -v|grep origin`.split[1]

FileUtils.mkdir_p deploy_dir

FileUtils.cd deploy_dir do
  unless File.exists?('.git')
    system "git init"
    system "git checkout -b gh-pages"
    system "git remote add origin #{repo_url}"
  end

  FileUtils.cp_r Dir.glob('../resources/public/*'), './'
  system "git checkout gh-pages"
  system "git add ."
  system "git add -u"
  system "git commit -m \"new deploy\""
  system "git push origin gh-pages --force"
end
