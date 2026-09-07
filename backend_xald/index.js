const express = require('express');
const app = express();

app.disable('x-powered-by');

app.use(express.json());

app.post('/api/v1/sync', (req, res) => {
  res.status(200).json({ status: 'success', synced_at: new Date().toISOString() });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`Backend XALD activo en puerto ${PORT}`));