#!/usr/bin/env ruby

require "rubygems"
require "bundler/setup"

require 'aws/s3'

bucket_name = ENV['AWS_BUCKET']

AWS::S3::Base.establish_connection!(
  :access_key_id     => ENV['AWS_ACCESS_KEY'],
  :secret_access_key => ENV['AWS_SECRET_KEY']
)

Dir.glob('resources/public/*').each do |asset|
  if asset =~ /index.html/
    contents = open(asset).read.gsub 'GOOGLE_ANALYTICS_TRACKING_CODE', ENV['GA_TRACKING_CODE']
  else
    contents = open(asset).read
  end
  AWS::S3::S3Object.store(File.basename(asset), contents, bucket_name, :access => :public_read)
end
