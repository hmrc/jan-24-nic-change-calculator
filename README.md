
# jan-24-nic-change-calculator

This service stores the anonymised results of calculations performed by the related frontend microservice `jan-24-nic-change-calculator-frontend`.  A worker emits metrics that aggregate the calculations in various ways, e.g. providing average salaries, total potential savings etc.

## How to run the service

You can run the service using service manager profile `JAN_24_NIC_CALC_ALL`, or locally with `sbt run`.  The service will run on port `11401`.

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").