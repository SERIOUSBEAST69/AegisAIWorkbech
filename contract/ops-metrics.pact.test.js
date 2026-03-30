const path = require('path');
const axios = require('axios');
const { PactV3, MatchersV3 } = require('@pact-foundation/pact');

const { like, string, number } = MatchersV3;

async function run() {
  const provider = new PactV3({
    consumer: 'aegis-frontend',
    provider: 'aegis-backend',
    dir: path.resolve(process.cwd(), 'contract/pacts'),
  });

  provider
    .given('ops metrics service is available')
    .uponReceiving('a web vital metric payload')
    .withRequest({
      method: 'POST',
      path: '/api/ops-metrics/web-vitals',
      headers: { 'Content-Type': 'application/json' },
      body: {
        name: string('LCP'),
        value: number(1520.8),
        rating: string('good'),
        id: string('pact-001'),
      },
    })
    .willRespondWith({
      status: 200,
      headers: { 'Content-Type': 'application/json' },
      body: {
        code: like(20000),
        msg: like('OK'),
        data: {
          accepted: like(true),
          metric: string('LCP'),
          value: number(1520.8),
          rating: string('good'),
        },
      },
    });

  await provider.executeTest(async (mockserver) => {
    const res = await axios.post(`${mockserver.url}/api/ops-metrics/web-vitals`, {
      name: 'LCP',
      value: 1520.8,
      rating: 'good',
      id: 'pact-001',
    });

    if (res.status !== 200 || res.data?.code !== 20000 || res.data?.data?.accepted !== true) {
      throw new Error('Pact verification failed for web vitals contract');
    }
  });

  console.log('Pact contract test passed');
}

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
